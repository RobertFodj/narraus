package com.narraus.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.narraus.NarrausTest;
import com.narraus.domain.AppUser;
import com.narraus.domain.UserService;
import com.vaadin.browserless.SecuredBrowserlessApplicationContext;
import com.vaadin.flow.component.Component;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;

/**
 * Puhelimen alapalkki (Viritinin MobileMainLayout): sama valikko kuin sivupaneelissa, rakennettu näkymien
 * {@code @MenuItem}-merkinnöistä; alisivut (kartta, ylläpito) eivät ole valikossa.
 */
@NarrausTest
class BottomBarTest {

    @Autowired
    private SecuredBrowserlessApplicationContext<Authentication> app;
    @Autowired
    private UserService users;

    private static com.vaadin.flow.component.html.Div bar(com.vaadin.browserless.BrowserlessUIContext window) {
        return window.find(com.vaadin.flow.component.html.Div.class).all().stream()
                .filter(d -> d.hasClassName("mobile-bottom-nav")).findFirst().orElseThrow();
    }

    private static List<String> labels(com.vaadin.browserless.BrowserlessUIContext window) {
        return bar(window).getChildren().map(c -> c.getElement().getAttribute("aria-label")).toList();
    }

    @Test
    void ordinaryUser_hasFiveTabs_likeTheSidePanel() {
        AppUser me = TestData.newUser(users, "Minä");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(FeedView.class);

            assertEquals(List.of("Saaliit", "Käyttäjät", "Keskustelut", "Kilpailut", "Oma tili"), labels(window));
        }
    }

    @Test
    void admin_hasNoExtraTab_adminIsOnTheOwnPage() {
        users.find("robbe").orElseGet(() -> users.register("robbe", "Robbe Ylläpitäjä", "salasana123"));

        try (var user = app.newUser("robbe", "USER")) {
            var window = user.newWindow();
            window.navigate(FeedView.class);

            assertEquals(5, labels(window).size());
            assertFalse(labels(window).contains("Ylläpito"));
            assertFalse(labels(window).contains("Kartta"), "Kartta on Saaliit-sivun välilehti");
        }
    }

    @Test
    void activeTab_followsTheCurrentView() {
        AppUser me = TestData.newUser(users, "Minä");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(UsersView.class);

            List<Component> active = bar(window).getChildren()
                    .filter(c -> "true".equals(c.getElement().getAttribute("highlight"))
                            || (c.getElement().hasAttribute("highlight") && !"false".equals(c.getElement().getAttribute("highlight"))))
                    .toList();
            assertEquals(1, active.size());
            assertEquals("Käyttäjät", active.get(0).getElement().getAttribute("aria-label"));
        }
    }

    @Test
    void logout_isOnTheOwnAccountPage_forPhonesWithoutTheSidePanel() {
        AppUser me = TestData.newUser(users, "Minä");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(ProfileView.class);

            assertTrue(window.find(com.vaadin.flow.component.button.Button.class).all().stream()
                    .anyMatch(b -> b.hasClassName("profile-logout") && "Kirjaudu ulos".equals(b.getText())));
        }
    }
}
