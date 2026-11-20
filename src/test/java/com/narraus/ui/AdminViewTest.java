package com.narraus.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.narraus.NarrausTest;
import com.narraus.domain.AdminService.UserOverview;
import com.narraus.domain.AppUser;
import com.narraus.domain.CompetitionEvent;
import com.narraus.domain.EventMetric;
import com.narraus.domain.EventService;
import com.narraus.domain.UserService;
import com.vaadin.browserless.BrowserlessUIContext;
import com.vaadin.browserless.SecuredBrowserlessApplicationContext;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.ListDataProvider;
import com.vaadin.flow.router.RouteNotFoundError;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;

/** Ylläpitäjälle näkyvät lisätiedot (tunnukset, kilpailut, app/web). Tavalliselle käyttäjälle mikään näistä ei näy. */
@NarrausTest
class AdminViewTest {

    @Autowired
    private SecuredBrowserlessApplicationContext<Authentication> app;
    @Autowired
    private UserService users;
    @Autowired
    private EventService events;

    private AppUser admin() {
        return users.find("robbe").orElseGet(() -> users.register("robbe", "Robbe Ylläpitäjä", "salasana123"));
    }

    private static boolean hasNavItem(BrowserlessUIContext window, String label) {
        return window.find(SideNavItem.class).all().stream().anyMatch(i -> label.equals(i.getLabel()));
    }

    @SuppressWarnings("unchecked")
    private static ListDataProvider<UserOverview> rows(Grid<UserOverview> grid) {
        return (ListDataProvider<UserOverview>) grid.getDataProvider();
    }

    private static boolean hasSpan(BrowserlessUIContext window, String text) {
        return window.find(Span.class).all().stream().anyMatch(s -> text.equals(s.getText()));
    }

    // ---- pääsy ----

    @Test
    void adminNavItem_isShownOnlyToTheAdmin() {
        admin();
        AppUser normal = TestData.newUser(users, "Tavallinen");

        try (var adminUser = app.newUser("robbe", "USER"); var normalUser = app.newUser(normal.getUsername(), "USER")) {
            var adminWindow = adminUser.newWindow();
            var normalWindow = normalUser.newWindow();
            adminWindow.navigate(FeedView.class);
            normalWindow.navigate(FeedView.class);

            assertTrue(hasNavItem(adminWindow, "Ylläpito"));
            assertFalse(hasNavItem(normalWindow, "Ylläpito"), "Tavalliselle ei näytetä ylläpitoa");
        }
    }

    @Test
    void adminPage_isNotFound_forNormalUsers() {
        AppUser normal = TestData.newUser(users, "Tavallinen");

        try (var user = app.newUser(normal.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate("admin", RouteNotFoundError.class); // ei paljasteta, että sivu on olemassa
        }
    }

    @Test
    void adminPage_isForLoggedInUsersOnly() {
        try (var anonymous = app.newUser()) {
            anonymous.newWindow().navigate("admin", LoginView.class);
        }
    }

    // ---- sisältö ----

    @Test
    @SuppressWarnings("unchecked")
    void adminPage_listsEveryUserWithUsernameEventsAndClient() {
        admin();
        AppUser racer = TestData.newUser(users, "Kisaaja");
        AppUser plain = TestData.newUser(users, "Rauhallinen");
        String eventName = "Ylläpitokisa-" + UUID.randomUUID().toString().substring(0, 6);
        CompetitionEvent event = events.create(admin(), eventName, "Kuvaus", Instant.now().minus(Duration.ofHours(1)),
                Instant.now().plus(Duration.ofHours(1)), EventMetric.WEIGHT, null, 3);
        events.join(event.getId(), racer);
        users.recordClient(racer.getId(), true);
        users.recordClient(racer.getId(), false);

        try (var adminUser = app.newUser("robbe", "USER")) {
            var window = adminUser.newWindow();
            window.navigate(AdminView.class);

            Grid<UserOverview> grid = window.find(Grid.class).single();
            var items = rows(grid).getItems().stream().toList();
            int racerRow = -1;
            int plainRow = -1;
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).user().equals(racer)) racerRow = i;
                if (items.get(i).user().equals(plain)) plainRow = i;
            }
            assertTrue(racerRow >= 0 && plainRow >= 0, "Kaikki käyttäjät listassa");

            var tester = window.test(grid);
            int username = grid.getColumns().indexOf(grid.getColumnByKey("username"));
            int eventsCol = grid.getColumns().indexOf(grid.getColumnByKey("events"));
            int client = grid.getColumns().indexOf(grid.getColumnByKey("client"));
            assertEquals(racer.getUsername(), tester.getCellText(racerRow, username), "Tunnus näkyy");
            assertEquals(eventName, tester.getCellText(racerRow, eventsCol), "Kilpailu näkyy");
            assertTrue(tester.getCellText(racerRow, client).startsWith("App ja web"), "Laitetyyppi näkyy");
            assertEquals("–", tester.getCellText(plainRow, eventsCol), "Ei kilpailuja");
            assertEquals("Ei tietoa", tester.getCellText(plainRow, client));
        }
    }

    @Test
    void adminPage_summaryCountsAppAndWebUsers() {
        admin();
        AppUser appUser = TestData.newUser(users, "Appi");
        users.recordClient(appUser.getId(), true);

        try (var adminUser = app.newUser("robbe", "USER")) {
            var window = adminUser.newWindow();
            window.navigate(AdminView.class);

            assertTrue(window.find(Span.class).all().stream().anyMatch(s -> s.getText().contains("käyttäjää")
                    && s.getText().contains("appia käyttänyt") && s.getText().contains("webiä käyttänyt")));
        }
    }

    @Test
    void adminPage_filterFindsByUsername() {
        admin();
        AppUser target = TestData.newUser(users, "Etsitty");

        try (var adminUser = app.newUser("robbe", "USER")) {
            var window = adminUser.newWindow();
            window.navigate(AdminView.class);
            @SuppressWarnings("unchecked")
            Grid<UserOverview> grid = window.find(Grid.class).single();

            window.test(window.find(TextField.class).single()).setValue(target.getUsername());

            var visible = rows(grid).fetch(new com.vaadin.flow.data.provider.Query<>()).toList();
            assertEquals(1, visible.size(), "Suodatus tunnuksella löytää täsmälleen yhden");
            assertEquals(target, visible.get(0).user());
        }
    }

    // ---- muualla näkyvät lisätiedot ----

    @Test
    void usersList_showsUsernames_toTheAdminOnly() {
        admin();
        AppUser other = TestData.newUser(users, "Kohde");
        AppUser normal = TestData.newUser(users, "Tavallinen");

        try (var adminUser = app.newUser("robbe", "USER"); var normalUser = app.newUser(normal.getUsername(), "USER")) {
            var adminWindow = adminUser.newWindow();
            var normalWindow = normalUser.newWindow();
            UsersView adminView = adminWindow.navigate(UsersView.class);
            UsersView normalView = normalWindow.navigate(UsersView.class);
            adminWindow.test(adminWindow.find(TextField.class).single()).setValue(other.getDisplayName());
            normalWindow.test(normalWindow.find(TextField.class).single()).setValue(other.getDisplayName());

            assertTrue(adminView.getElement().getOuterHTML().contains(other.getUsername()),
                    "Ylläpitäjä näkee tunnuksen");
            assertFalse(normalView.getElement().getOuterHTML().contains(other.getUsername()),
                    "Tavallinen käyttäjä ei näe tunnusta");
        }
    }

    @Test
    void userPage_showsAdminInfo_toTheAdminOnly() {
        admin();
        AppUser other = TestData.newUser(users, "Kohde");
        AppUser normal = TestData.newUser(users, "Tavallinen");
        users.recordClient(other.getId(), true);

        try (var adminUser = app.newUser("robbe", "USER"); var normalUser = app.newUser(normal.getUsername(), "USER")) {
            var adminWindow = adminUser.newWindow();
            var normalWindow = normalUser.newWindow();
            adminWindow.navigate("user/" + other.getId(), UserProfileView.class);
            normalWindow.navigate("user/" + other.getId(), UserProfileView.class);

            assertTrue(adminWindow.find(H3.class).all().stream().anyMatch(h -> "Ylläpitäjän tiedot".equals(h.getText())));
            assertTrue(hasSpan(adminWindow, "Tunnus: " + other.getUsername()));
            assertTrue(hasSpan(adminWindow, "Laite: App"));
            assertTrue(hasSpan(adminWindow, "Kilpailut: ei yhtään"));
            assertTrue(normalWindow.find(H3.class).all().stream().noneMatch(h -> "Ylläpitäjän tiedot".equals(h.getText())));
            assertFalse(normalWindow.find(Span.class).all().stream().anyMatch(s -> s.getText().startsWith("Tunnus:")),
                    "Tavallinen käyttäjä ei näe tunnusta eikä laitetyyppiä");
        }
    }
}
