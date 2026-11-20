package com.narraus.ui;

import com.narraus.domain.AppUser;
import com.narraus.domain.CompetitionEvent;
import com.narraus.domain.EventService;
import com.narraus.domain.EventStatus;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import jakarta.annotation.security.PermitAll;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Kilpailulista. Kuka tahansa voi katsoa ja liittyä; vain ylläpitäjä näkee Uusi kilpailu -napin. */
@Route(value = "events", layout = MainLayout.class)
@PageTitle("Kilpailut | Narraus")
@PermitAll
public class EventsView extends VerticalLayout {

    private final EventService events;
    private final AppUser me;
    private final boolean admin;
    private final VerticalLayout list = new VerticalLayout();

    public EventsView(EventService events, CurrentUser currentUser) {
        this.events = events;
        this.me = currentUser.get();
        this.admin = events.isAdmin(me);
        setMaxWidth("44em");
        getStyle().set("margin", "0 auto");

        HorizontalLayout header = new HorizontalLayout(new H2("Kilpailut"));
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        if (admin) {
            Button create = new Button("Uusi kilpailu", VaadinIcon.PLUS.create(),
                    e -> new EventDialog(events, currentUser, null, saved ->
                            UI.getCurrent().navigate(EventView.class, saved.getId())).open());
            create.addThemeVariants(ButtonVariant.PRIMARY);
            create.addClassName("event-create");
            header.add(create);
        }
        VerticalLayout bar = new VerticalLayout(header);
        bar.addClassName("island");
        bar.setPadding(false);
        bar.setSpacing(false);

        list.setPadding(false);
        add(bar, list);
        refresh();
    }

    private void refresh() {
        list.removeAll();
        List<CompetitionEvent> all = events.all();
        if (all.isEmpty()) {
            list.add(new Paragraph(admin ? "Ei kilpailuja vielä. Luo ensimmäinen Uusi kilpailu -napista."
                    : "Ei kilpailuja vielä. Ylläpitäjä lisää niitä tänne."));
            return;
        }
        Map<EventStatus, List<CompetitionEvent>> byStatus = all.stream()
                .collect(Collectors.groupingBy(CompetitionEvent::status));
        section("Käynnissä", byStatus.get(EventStatus.ONGOING));
        section("Tulossa", byStatus.get(EventStatus.UPCOMING));
        section("Päättyneet", byStatus.get(EventStatus.ENDED));
    }

    private void section(String title, List<CompetitionEvent> items) {
        if (items == null || items.isEmpty()) return;
        Span t = new Span(title + " (" + items.size() + ")");
        t.addClassName("event-section");
        list.add(t);
        items.forEach(e -> list.add(card(e)));
    }

    private RouterLink card(CompetitionEvent e) {
        RouterLink link = new RouterLink();
        link.setRoute(EventView.class, e.getId());
        link.addClassName("event-card");

        H3 name = new H3(e.getName());
        name.addClassName("event-card-title");
        Span badge = new Span(e.status().label());
        badge.addClassName("event-badge");
        badge.addClassNames(EventFormat.statusClass(e.status()).split(" "));
        HorizontalLayout top = new HorizontalLayout(name, badge);
        top.setAlignItems(FlexComponent.Alignment.CENTER);
        top.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        top.setWidthFull();

        Div goal = new Div(new Span("🏆 " + EventFormat.goal(e)));
        Div when = new Div(new Span("🕒 " + EventFormat.interval(e)));
        long count = events.participantCount(e);
        String who = count + (count == 1 ? " osallistuja" : " osallistujaa")
                + (events.isParticipant(e, me) ? " · Olet mukana" : "");
        Div people = new Div(new Span("👥 " + who));
        for (Div d : List.of(goal, when, people)) d.addClassName("event-line");

        link.add(top, goal, when, people);
        return link;
    }
}
