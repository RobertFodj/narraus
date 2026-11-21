package com.narraus.ui;

import com.narraus.domain.AppUser;
import com.narraus.domain.NotificationService;
import com.narraus.domain.NotificationService.Entry;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.QueryParameters;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Kellokuvake: avaa listan muista ilmoituksista (uusi seuraaja, tykkäys, uusi kilpailu, Narraus-viestit).
 * Chat-viestit eivät ole tässä, ne ovat Keskusteluissa. Merkki näyttää lukemattomien määrän.
 */
class NotificationBell extends Button {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("d.M.yyyy HH:mm")
            .withZone(ZoneId.systemDefault());

    private final NotificationService notifications;
    private final AppUser me;
    private final Span badge = new Span();

    NotificationBell(NotificationService notifications, AppUser me) {
        super(VaadinIcon.BELL.create());
        this.notifications = notifications;
        this.me = me;
        addThemeVariants(ButtonVariant.TERTIARY);
        addClassName("notification-bell");
        getElement().setAttribute("aria-label", "Ilmoitukset");
        getElement().setAttribute("title", "Ilmoitukset");
        badge.addClassName("nav-badge");
        getElement().appendChild(badge.getElement());
        addClickListener(e -> open());
        refresh();
    }

    /** Päivittää lukemattomien merkin. */
    void refresh() {
        long unread = notifications.unreadCount(me);
        badge.setVisible(unread > 0);
        badge.setText(unread > 0 ? MainLayout.badgeText(unread) : "");
        badge.getElement().setAttribute("aria-label", unread + " lukematonta ilmoitusta");
    }

    private void open() {
        List<Entry> entries = notifications.recent(me);
        Dialog dialog = new Dialog();
        dialog.addClassName("notification-dialog");
        dialog.setHeaderTitle("Ilmoitukset");
        dialog.setWidth("min(28em, 95vw)");

        VerticalLayout list = new VerticalLayout();
        list.setPadding(false);
        list.setSpacing(false);
        if (entries.isEmpty()) {
            list.add(new Paragraph("Ei ilmoituksia."));
        }
        for (Entry entry : entries) list.add(row(entry, dialog));
        dialog.add(list);
        dialog.getFooter().add(new Button("Sulje", e -> dialog.close()));
        dialog.open();

        // Lista on nyt nähty: kaikki merkitään luetuiksi (rivit pysyvät tässä näkymässä lihavoituina)
        if (notifications.markAllRead(me)) {
            UnreadChanged.fire(UI.getCurrent());
        }
        refresh();
    }

    private Div row(Entry entry, Dialog dialog) {
        Span text = new Span(describe(entry));
        text.addClassName("notification-text");
        Span time = new Span(TIME.format(entry.createdAt()));
        time.addClassName("notification-time");
        Div row = new Div(text, time);
        row.addClassName("notification-row");
        if (entry.unread()) row.addClassName("notification-unread");
        row.getElement().setAttribute("role", "link");
        row.addClickListener(e -> {
            dialog.close();
            navigate(entry);
        });
        return row;
    }

    /** Ilmoituksen teksti (suomeksi; muille kielille käännetään selaimessa). */
    static String describe(Entry entry) {
        if (entry.systemMessage()) return "Narraus: " + entry.label();
        String who = entry.actor() == null ? "" : entry.actor().getDisplayName();
        return switch (entry.kind()) {
            case FOLLOWER -> who + " alkoi seurata sinua";
            case LIKE -> who + " tykkäsi julkaisustasi: " + Species.display(entry.label());
            case EVENT -> "Uusi kilpailu: " + entry.label();
        };
    }

    private void navigate(Entry entry) {
        UI ui = UI.getCurrent();
        if (entry.systemMessage()) {
            ui.navigate(ChatView.class);
            return;
        }
        switch (entry.kind()) {
            case FOLLOWER -> ui.navigate(UserProfileView.class, entry.actor().getId());
            case LIKE -> ui.navigate(FeedView.class, QueryParameters.of("post", String.valueOf(entry.targetId())));
            case EVENT -> ui.navigate(EventView.class, entry.targetId());
        }
    }
}
