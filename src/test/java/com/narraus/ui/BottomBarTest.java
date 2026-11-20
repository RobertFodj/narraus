package com.narraus.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.narraus.NarrausTest;
import com.narraus.domain.AppUser;
import com.narraus.domain.UserService;
import com.vaadin.browserless.SecuredBrowserlessApplicationContext;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Nav;
import com.vaadin.flow.router.RouterLink;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;

/** Puhelimen alapalkki: samat valikot kuin sivupaneelissa; Ylläpito on ylläpitäjän omalla sivulla. */
@NarrausTest
class BottomBarTest {

    @Autowired
    private SecuredBrowserlessApplicationContext<Authentication> app;
    @Autowired
    private UserService users;

    private static List<String> labels(com.vaadin.browserless.BrowserlessUIContext window) {
        Nav bar = window.find(Nav.class).all().stream().filter(n -> n.hasClassName("bottom-bar")).findFirst().orElseThrow();
        return bar.getChildren().map(c -> ((RouterLink) c).getElement().getAttribute("aria-label")).toList();
    }

    @Test
    void ordinaryUser_hasFiveTabs_likeTheSidePanel() {
        AppUser me = TestData.newUser(users, "Minä");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(FeedView.class);

            assertEquals(List.of("Saaliit", "Käyttäjät", "Viestit", "Kilpailut", "Oma tili"), labels(window));
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
        }
    }

    @Test
    void activeTab_followsTheCurrentView() {
        AppUser me = TestData.newUser(users, "Minä");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(UsersView.class);

            Nav bar = window.find(Nav.class).all().stream().filter(n -> n.hasClassName("bottom-bar")).findFirst().orElseThrow();
            List<Component> active = bar.getChildren()
                    .filter(c -> "page".equals(c.getElement().getAttribute("aria-current"))).toList();
            assertEquals(1, active.size());
            assertEquals("Käyttäjät", active.get(0).getElement().getAttribute("aria-label"));
        }
    }

    @Test
    void accountButton_isInTheTopBar() {
        AppUser me = TestData.newUser(users, "Minä");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(FeedView.class);

            assertTrue(window.find(com.vaadin.flow.component.button.Button.class).all().stream()
                    .anyMatch(b -> b.hasClassName("account-button") && me.getDisplayName().equals(b.getText())),
                    "Tilipainikkeen teksti on käyttäjän nimi");
        }
    }

    @Test
    void styles_makeAFloatingIsland_onlyOnPortraitPhones() throws Exception {
        String css;
        try (var in = BottomBarTest.class.getResourceAsStream("/META-INF/resources/styles.css")) {
            css = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        assertTrue(css.contains(".bottom-bar,\n.account-button {\n  display: none;"), "Oletuksena piilossa");
        int media = css.indexOf("@media (max-width: 700px) and (orientation: portrait)");
        assertTrue(media >= 0, "Vain pystyssä oleva puhelin");
        String rules = css.substring(media);
        assertTrue(rules.contains("position: fixed") && rules.contains("left: 12px") && rules.contains("right: 12px"),
                "Irti sivuseinistä");
        assertTrue(rules.contains("bottom: max(12px, env(safe-area-inset-bottom))"), "Irti alareunasta");
        assertTrue(rules.contains("border-radius: 32px"), "Pyöreät kulmat");
        assertTrue(rules.contains("vaadin-drawer-toggle {\n    display: none;"), "Sivupaneeli pois pystyssä");
    }
}
