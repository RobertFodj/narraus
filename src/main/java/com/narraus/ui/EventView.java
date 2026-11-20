package com.narraus.ui;

import com.narraus.domain.AppUser;
import com.narraus.domain.CompetitionEvent;
import com.narraus.domain.EventFishReview;
import com.narraus.domain.EventMetric;
import com.narraus.domain.EventService;
import com.narraus.domain.EventStatus;
import com.narraus.domain.FeedBroadcaster;
import com.narraus.domain.EventBroadcaster;
import com.narraus.domain.GeocodingService;
import com.narraus.domain.PostService;
import com.narraus.domain.ReviewStatus;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.NotFoundException;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;
import com.vaadin.flow.component.UIDetachedException;
import com.vaadin.flow.shared.Registration;
import jakarta.annotation.security.PermitAll;
import java.util.List;
import java.util.function.Consumer;

/**
 * Yksittäinen kilpailu: tiedot, liittyminen, tulostaulu ja keskustelu. Ylläpitäjä näkee lisäksi muokkaus- ja
 * poistonapit sekä kalojen tarkistustyökalut (kysy onko oikea, poista kisasta, palauta).
 */
@Route(value = "event", layout = MainLayout.class)
@PageTitle("Kilpailu | Narraus")
@PermitAll
public class EventView extends VerticalLayout implements HasUrlParameter<Long> {

    private final EventService events;
    private final PostService posts;
    private final CurrentUser currentUser;
    private final MapConfig mapConfig;
    private final GeocodingService geocoder;
    private final EventBroadcaster eventBroadcaster;
    private final FeedBroadcaster feedBroadcaster;

    private Long eventId;
    private AppUser me;
    private boolean admin;
    private final Tabs tabs = new Tabs(new Tab("Tulokset"), new Tab("Keskustelu"));
    private final VerticalLayout content = new VerticalLayout();
    private EventChatPanel chat;
    private Registration eventRegistration;
    private Registration feedRegistration;

    public EventView(EventService events, PostService posts, CurrentUser currentUser, MapConfig mapConfig,
                     GeocodingService geocoder, EventBroadcaster eventBroadcaster, FeedBroadcaster feedBroadcaster) {
        this.events = events;
        this.posts = posts;
        this.currentUser = currentUser;
        this.mapConfig = mapConfig;
        this.geocoder = geocoder;
        this.eventBroadcaster = eventBroadcaster;
        this.feedBroadcaster = feedBroadcaster;
        addClassName("event-view");
        setMaxWidth("44em");
        setAlignItems(FlexComponent.Alignment.STRETCH);
        getStyle().set("margin", "0 auto");
        content.setPadding(false);
        tabs.addSelectedChangeListener(e -> showTab());
    }

    @Override
    public void setParameter(BeforeEvent event, Long id) {
        this.eventId = id;
        this.me = currentUser.get();
        this.admin = events.isAdmin(me);
        if (events.find(id).isEmpty()) {
            event.rerouteToError(NotFoundException.class, "Kilpailua ei löytynyt");
            return;
        }
        render();
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        UI ui = attachEvent.getUI();
        eventRegistration = eventBroadcaster.register(id -> {
            if (id.equals(eventId)) pushToUi(ui, this::refreshLive);
        });
        feedRegistration = feedBroadcaster.register(postId -> pushToUi(ui, this::refreshResultsIfShown));
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        if (eventRegistration != null) eventRegistration.remove();
        if (feedRegistration != null) feedRegistration.remove();
    }

    private static void pushToUi(UI ui, Runnable action) {
        try {
            ui.access(action::run);
        } catch (UIDetachedException ignored) {
            // selain on jo suljettu
        }
    }

    // ---- sivun rakenne ----

    /** Rakentaa koko sivun uudelleen (liittyminen, muokkaus). Valittu välilehti säilyy. */
    private void render() {
        int selected = Math.max(0, tabs.getSelectedIndex());
        CompetitionEvent ev = events.find(eventId).orElse(null);
        if (ev == null) {
            Notification.show("Kilpailu on poistettu");
            UI.getCurrent().navigate(EventsView.class);
            return;
        }
        removeAll();
        EventStatus status = ev.status();

        BackButton back = new BackButton(EventsView.class);
        H2 title = new H2(EventFormat.name(ev));
        title.addClassName("event-title");
        Span badge = new Span(status.label());
        badge.addClassName("event-badge");
        badge.addClassNames(EventFormat.statusClass(status).split(" "));
        HorizontalLayout head = new HorizontalLayout(back, title, badge);
        head.setAlignItems(FlexComponent.Alignment.CENTER);
        head.setWidthFull();
        head.setFlexGrow(1, title);
        head.addClassName("island");
        add(head);

        add(infoSection(ev));
        add(actions(ev, status));

        tabs.setSelectedIndex(selected);
        content.removeAll();
        add(tabs, content);
        chat = null;
        showTab();
    }

    private Div infoSection(CompetitionEvent ev) {
        Div info = new Div();
        info.addClassName("event-info");
        if (EventFormat.description(ev) != null) {
            Paragraph description = new Paragraph(EventFormat.description(ev));
            description.getStyle().set("white-space", "pre-wrap");
            info.add(description);
        }
        for (String line : List.of("🏆 " + EventFormat.goal(ev), "🕒 " + EventFormat.interval(ev),
                "👥 " + events.participantCount(ev) + " osallistujaa", "📏 " + scoringRule(ev))) {
            Div d = new Div(new Span(line));
            d.addClassName("event-line");
            info.add(d);
        }
        Div photoRule = new Div(new Span("📷 Vain kuvalliset saaliit lasketaan mukaan kilpailuun."));
        photoRule.addClassNames("event-line", "event-photo-rule");
        info.add(photoRule);
        return info;
    }

    private static String scoringRule(CompetitionEvent ev) {
        if (ev.getMetric() == EventMetric.COUNT) return "Voittaa se, jolla on eniten kaloja.";
        if (ev.getCountedFish() != 1) return "Tulos on " + ev.getCountedFish() + " parhaan kalan summa.";
        return switch (ev.getMetric()) {
            case WEIGHT -> "Voittaa se, jolla on painavin kala.";
            case LENGTH -> "Voittaa se, jolla on pisin kala.";
            default -> "Voittaa se, jolla on suurin ympärysmitta.";
        };
    }

    private HorizontalLayout actions(CompetitionEvent ev, EventStatus status) {
        HorizontalLayout row = new HorizontalLayout();
        row.addClassName("event-actions");
        row.getStyle().set("flex-wrap", "wrap");
        boolean participant = events.isParticipant(ev, me);

        if (status != EventStatus.ENDED) {
            if (participant) {
                Button leave = new Button("Poistu kilpailusta", e -> {
                    events.leave(eventId, me);
                    render();
                });
                leave.addClassName("event-leave");
                row.add(leave);
            } else {
                Button join = new Button("Liity kilpailuun", VaadinIcon.PLUS.create(), e -> {
                    try {
                        events.join(eventId, me);
                        render();
                    } catch (IllegalArgumentException ex) {
                        Notification.show(ex.getMessage()).addThemeVariants(NotificationVariant.ERROR);
                    }
                });
                join.addThemeVariants(ButtonVariant.PRIMARY);
                join.addClassName("event-join");
                row.add(join);
            }
        } else if (participant) {
            row.add(new Span("✔ Osallistuit tähän kilpailuun"));
        }
        if (participant && status == EventStatus.ONGOING) {
            Button add = new Button("Lisää saalis", VaadinIcon.CAMERA.create(),
                    e -> new CatchDialog(posts, currentUser, mapConfig, geocoder, this::refreshResultsIfShown).open());
            add.addClassName("event-add-catch");
            row.add(add);
        }
        if (admin) {
            Button edit = new Button("Muokkaa kilpailua", VaadinIcon.EDIT.create(),
                    e -> new EventDialog(events, currentUser, ev, saved -> render()).open());
            edit.addClassName("event-edit");
            Button delete = new Button("Poista kilpailu", VaadinIcon.TRASH.create(), e -> confirmDelete(ev));
            delete.addThemeVariants(ButtonVariant.ERROR);
            delete.addClassName("event-delete");
            row.add(edit, delete);
        }
        return row;
    }

    private void confirmDelete(CompetitionEvent ev) {
        ConfirmDialog confirm = new ConfirmDialog();
        confirm.setHeader("Poistetaanko kilpailu?");
        confirm.setText("Kilpailu \"" + EventFormat.name(ev) + "\", sen osallistujat, tarkistukset ja keskustelu poistetaan "
                + "pysyvästi. Saaliit säilyvät.");
        confirm.setCancelable(true);
        confirm.setCancelText("Peruuta");
        confirm.setConfirmText("Poista");
        confirm.setConfirmButtonTheme("error primary");
        confirm.addConfirmListener(e -> {
            events.delete(eventId, me);
            UI.getCurrent().navigate(EventsView.class);
        });
        confirm.open();
    }

    // ---- välilehdet ----

    private void showTab() {
        content.removeAll();
        if (tabs.getSelectedIndex() == 1) {
            chat = new EventChatPanel(events, eventId, me);
            content.add(chat);
        } else {
            chat = null;
            renderResults();
        }
    }

    /** Jos ylläpitäjä on poistanut kilpailun, katsoja ohjataan listaan eikä yritetä päivittää poistettua. */
    private boolean leaveIfDeleted() {
        if (events.find(eventId).isPresent()) return false;
        Notification.show("Kilpailu on poistettu");
        UI.getCurrent().navigate(EventsView.class);
        return true;
    }

    private void refreshLive() {
        if (leaveIfDeleted()) return;
        if (tabs.getSelectedIndex() == 1 && chat != null) chat.refresh();
        else renderResults();
    }

    private void refreshResultsIfShown() {
        if (leaveIfDeleted()) return;
        if (tabs.getSelectedIndex() == 0) renderResults();
    }

    // ---- tulostaulu ----

    private void renderResults() {
        if (tabs.getSelectedIndex() != 0) return;
        content.removeAll();
        EventService.Leaderboard lb = events.leaderboard(eventId);
        CompetitionEvent ev = lb.event();
        boolean ended = ev.status() == EventStatus.ENDED;
        EventMetric metric = ev.getMetric();

        Span title = new Span(ended ? "🏁 Lopputulokset" : "📊 Tilanne nyt");
        title.addClassName("event-results-title");
        content.add(title);

        if (lb.standings().isEmpty()) {
            content.add(new Paragraph(ended
                    ? "Kilpailussa ei ollut tuloksia."
                    : "Ei vielä tuloksia. Tulokseksi kelpaa kuvallinen saalis, joka on pyydetty kilpailun aikana."));
        }
        for (EventService.Standing s : lb.standings()) {
            content.add(standingCard(s, metric, ended));
        }
        content.add(new Span("Osallistujia yhteensä: " + lb.participants()));

        if (!lb.rejected().isEmpty()) {
            if (admin) {
                Span rejectedTitle = new Span("🚫 Poistetut kalat (" + lb.rejected().size() + ")");
                rejectedTitle.addClassName("event-section");
                content.add(rejectedTitle);
                lb.rejected().forEach(r -> content.add(rejectedRow(r, ev)));
            } else {
                Span note = new Span(lb.rejected().size() + " kalaa on poistettu kilpailusta ylläpitäjän päätöksellä.");
                note.addClassName("event-muted");
                content.add(note);
            }
        }
    }

    private Div standingCard(EventService.Standing s, EventMetric metric, boolean ended) {
        Div card = new Div();
        card.addClassName("standing");
        if (s.rank() <= 3) card.addClassName(ended ? "podium-" + s.rank() : "top-" + s.rank());
        if (ended && s.rank() <= 3) card.addClassName("podium");

        Span rank = new Span(medal(s.rank()));
        rank.addClassName("standing-rank");
        Span name = new Span(s.user().getDisplayName());
        name.addClassName("standing-name");
        Span score = new Span(EventService.format(s.score(), metric));
        score.addClassName("standing-score");
        Div head = new Div(rank, name, score);
        head.addClassName("standing-head");
        card.add(head);

        CompetitionEvent ev = events.find(eventId).orElseThrow();
        for (EventService.CountedFish f : s.fish()) card.add(fishRow(f, ev));
        return card;
    }

    private static String medal(int rank) {
        return switch (rank) {
            case 1 -> "🥇";
            case 2 -> "🥈";
            case 3 -> "🥉";
            default -> rank + ".";
        };
    }

    /** Kilpailun saaliista hypätään alkuperäiseen julkaisuun Saaliit-sivulla. */
    private static void openOriginal(Long postId) {
        UI.getCurrent().navigate(FeedView.class,
                com.vaadin.flow.router.QueryParameters.of("post", String.valueOf(postId)));
    }

    private Div fishRow(EventService.CountedFish f, CompetitionEvent ev) {
        Div row = new Div();
        row.addClassName("event-fish");

        String file = f.post().getPhotoFile();
        if (file != null) {
            Image thumb = new Image(new StreamResource(file, () -> posts.openThumbnail(file)), f.post().getSpecies());
            thumb.addClassName("event-fish-thumb");
            thumb.addClassName("event-fish-link");
            thumb.getElement().setAttribute("loading", "lazy");
            thumb.addClickListener(e -> openOriginal(f.post().getId()));
            row.add(thumb);
        }

        String text = Species.display(f.post().getSpecies())
                + (ev.getMetric() == EventMetric.COUNT ? "" : " · " + EventService.format(f.value(), ev.getMetric()))
                + " · " + EventFormat.SHORT.format(f.post().getCaughtAt());
        Div body = new Div(new Span(text));
        body.addClassName("event-fish-text");
        body.addClassName("event-fish-link");
        body.getElement().setAttribute("role", "link");
        body.addClickListener(e -> openOriginal(f.post().getId())); // alkuperäinen julkaisu Saaliit-sivulla
        if (f.questioned()) {
            Span flag = new Span("⚠ Tarkistuksessa" + (f.review().getNote() == null ? "" : ": " + f.review().getNote()));
            flag.addClassName("event-flag");
            body.add(flag);
        }
        row.add(body);

        if (admin) row.add(fishAdminButtons(f));
        return row;
    }

    private HorizontalLayout fishAdminButtons(EventService.CountedFish f) {
        HorizontalLayout buttons = new HorizontalLayout();
        buttons.addClassName("event-fish-admin");
        buttons.getStyle().set("flex-wrap", "wrap");
        Long postId = f.post().getId();

        if (f.questioned()) {
            Button approve = new Button("Hyväksy", VaadinIcon.CHECK.create(), e -> {
                events.clearReview(eventId, postId, me);
                renderResults();
            });
            approve.addClassName("fish-approve");
            buttons.add(approve);
        } else {
            Button ask = new Button("Kysy onko oikea", VaadinIcon.QUESTION_CIRCLE.create(), e -> noteDialog(
                    "Kysy, onko saalis oikea", "Kysymys (näkyy kilpailun keskustelussa)",
                    "Onko tämä saalis oikea?", "Kysy", note -> review(postId, ReviewStatus.QUESTIONED, note)));
            ask.addClassName("fish-ask");
            buttons.add(ask);
        }
        Button remove = new Button("Poista kisasta", VaadinIcon.TRASH.create(), e -> noteDialog(
                "Poista saalis kilpailusta", "Syy (näkyy kilpailun keskustelussa)", "", "Poista",
                note -> review(postId, ReviewStatus.REJECTED, note)));
        remove.addThemeVariants(ButtonVariant.ERROR);
        remove.addClassName("fish-remove");
        buttons.add(remove);
        for (var c : buttons.getChildren().toList()) {
            if (c instanceof Button b) b.addThemeVariants(ButtonVariant.SMALL);
        }
        return buttons;
    }

    private Div rejectedRow(EventFishReview r, CompetitionEvent ev) {
        Div row = new Div();
        row.addClassName("event-fish");
        String text = r.getPost().getAuthor().getDisplayName() + " · " + events.describe(ev, r.getPost())
                + (r.getNote() == null ? "" : " · " + r.getNote());
        Div body = new Div(new Span(text));
        body.addClassName("event-fish-text");
        body.addClassName("event-fish-link");
        body.addClickListener(e -> openOriginal(r.getPost().getId()));
        Button restore = new Button("Palauta", VaadinIcon.ARROW_BACKWARD.create(), e -> {
            events.clearReview(eventId, r.getPost().getId(), me);
            renderResults();
        });
        restore.addThemeVariants(ButtonVariant.SMALL);
        restore.addClassName("fish-restore");
        row.add(body, restore);
        return row;
    }

    private void review(Long postId, ReviewStatus status, String note) {
        try {
            events.review(eventId, postId, me, status, note);
            renderResults();
        } catch (IllegalArgumentException | SecurityException ex) {
            Notification.show(ex.getMessage()).addThemeVariants(NotificationVariant.ERROR);
        }
    }

    private void noteDialog(String title, String label, String initial, String confirmText, Consumer<String> action) {
        Dialog d = new Dialog();
        d.setHeaderTitle(title);
        d.setWidth("min(30em, 95vw)");
        TextArea note = new TextArea(label);
        note.setWidthFull();
        note.setMaxLength(500);
        note.setValue(initial);
        d.add(note);
        Button ok = new Button(confirmText, e -> {
            d.close();
            action.accept(note.getValue());
        });
        ok.addThemeVariants(ButtonVariant.PRIMARY);
        d.getFooter().add(new Button("Peruuta", e -> d.close()), ok);
        d.open();
    }
}
