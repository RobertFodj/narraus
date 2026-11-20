package com.narraus.ui;

import com.narraus.domain.AdminPolicy;
import com.narraus.domain.AdminService;
import com.narraus.domain.AdminService.UserOverview;
import com.narraus.domain.CompetitionEvent;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.ListDataProvider;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.NotFoundException;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Ylläpito: kaikki käyttäjät tunnuksineen, kilpailuineen ja laitetyyppeineen (app vai web). Vain ylläpitäjälle.
 * Muille sivu näyttäytyy olemattomana (ei paljasteta, että se on olemassa).
 */
@Route(value = "admin", layout = MainLayout.class)
@PageTitle("Ylläpito | Narraus")
@PermitAll
public class AdminView extends VerticalLayout implements BeforeEnterObserver {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("d.M.yyyy HH:mm")
            .withZone(ZoneId.systemDefault());

    private final AdminService adminService;
    private final AdminPolicy adminPolicy;
    private final CurrentUser currentUser;

    public AdminView(AdminService adminService, AdminPolicy adminPolicy, CurrentUser currentUser) {
        this.adminService = adminService;
        this.adminPolicy = adminPolicy;
        this.currentUser = currentUser;
        setSizeFull();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        var me = currentUser.get();
        if (!adminPolicy.isAdmin(me)) {
            event.rerouteToError(NotFoundException.class);
            return;
        }
        removeAll();
        List<UserOverview> all = adminService.overview(me);

        long app = all.stream().filter(UserOverview::usesApp).count();
        long web = all.stream().filter(UserOverview::usesWeb).count();
        Span summary = new Span(all.size() + " käyttäjää  ·  appia käyttänyt " + app + "  ·  webiä käyttänyt " + web);

        ListDataProvider<UserOverview> data = new ListDataProvider<>(all);
        Grid<UserOverview> grid = new Grid<>();
        grid.setDataProvider(data);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        grid.addColumn(o -> o.user().getDisplayName()).setHeader("Käyttäjä").setKey("name").setAutoWidth(true);
        grid.addColumn(UserOverview::username).setHeader("Tunnus").setKey("username").setAutoWidth(true);
        grid.addColumn(o -> eventNames(o.events())).setHeader("Kilpailut").setKey("events").setAutoWidth(true)
                .setFlexGrow(1);
        grid.addColumn(o -> lastUsedText(o)).setHeader("Viimeksi käytetty").setKey("last").setAutoWidth(true)
                .setComparator(java.util.Comparator.comparing(UserOverview::lastUsedAt,
                        java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder())));
        grid.addColumn(o -> clientText(o)).setHeader("Laitteet").setKey("client").setAutoWidth(true);
        grid.addColumn(UserOverview::pushDevices).setHeader("Ilmoituslaitteita").setKey("push");
        grid.addColumn(UserOverview::posts).setHeader("Julkaisuja").setKey("posts");
        grid.setSizeFull();

        TextField filter = new TextField();
        filter.setPlaceholder("Hae nimellä tai tunnuksella…");
        filter.setClearButtonVisible(true);
        filter.setValueChangeMode(ValueChangeMode.LAZY);
        filter.setWidth("22em");
        filter.addValueChangeListener(e -> {
            String q = e.getValue() == null ? "" : e.getValue().trim().toLowerCase();
            data.setFilter(o -> q.isEmpty() || o.user().getDisplayName().toLowerCase().contains(q)
                    || o.username().toLowerCase().contains(q));
        });

        add(new H2("Ylläpito"), summary, filter, grid);
    }

    static String eventNames(List<CompetitionEvent> events) {
        return events.isEmpty() ? "–" : events.stream().map(CompetitionEvent::getName).collect(Collectors.joining(", "));
    }

    /** Esim. "App ja web (app 20.11.2026 10:00, web 19.11.2026 09:00)". */
    static String clientText(UserOverview o) {
        if (!o.usesApp() && !o.usesWeb()) return o.clientLabel();
        StringBuilder sb = new StringBuilder(o.clientLabel()).append(" (");
        if (o.usesApp()) sb.append("app ").append(TIME.format(o.appLastUsedAt()));
        if (o.usesApp() && o.usesWeb()) sb.append(", ");
        if (o.usesWeb()) sb.append("web ").append(TIME.format(o.webLastUsedAt()));
        return sb.append(")").toString();
    }

    /** Esim. "App 20.11.2026 10:05" (viimeisin käyttö), tai "–" jos tietoa ei ole. */
    static String lastUsedText(UserOverview o) {
        return o.lastClient() == null ? "–" : o.lastClient() + " " + TIME.format(o.lastUsedAt());
    }
}
