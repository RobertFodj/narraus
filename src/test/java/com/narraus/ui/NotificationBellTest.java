package com.narraus.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.narraus.NarrausTest;
import com.narraus.domain.AppUser;
import com.narraus.domain.SystemMessageService;
import com.narraus.domain.UserService;
import com.vaadin.browserless.BrowserlessUIContext;
import com.vaadin.browserless.SecuredBrowserlessApplicationContext;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;

/** Kellokuvake, ylläpitäjän pikapainikkeet kotisivulla ja käyttöehdot Oma tili -sivulla. */
@NarrausTest
class NotificationBellTest {

    @Autowired
    private SecuredBrowserlessApplicationContext<Authentication> app;
    @Autowired
    private UserService users;
    @Autowired
    private SystemMessageService system;

    private AppUser admin() {
        return users.find("robbe").orElseGet(() -> users.register("robbe", "Robbe Ylläpitäjä", "salasana123"));
    }

    private static Button bell(BrowserlessUIContext window) {
        return window.find(Button.class).all().stream().filter(b -> b.hasClassName("notification-bell")).findFirst()
                .orElse(null);
    }

    private static String badgeText(Button bell) {
        return bell.getElement().getChildren().filter(c -> c.getClassList().contains("nav-badge")).findFirst()
                .map(c -> c.getText()).orElse("");
    }

    // ---- kellokuvake ----

    @Test
    void bell_isOnEveryPage_andShowsTheUnreadCount() {
        AppUser me = TestData.newUser(users, "Minä");
        AppUser fan = TestData.newUser(users, "Fani");
        users.toggleFollow(fan, me);
        system.send(admin(), Set.of(me), "Narraus-viesti");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(FeedView.class);
            assertTrue(bell(window) != null, "Kellokuvake Saaliit-sivulla");
            assertEquals("2", badgeText(bell(window)), "Seuraaja ja Narraus-viesti");

            window.navigate(UsersView.class);
            assertTrue(bell(window) != null, "Kellokuvake myös muilla sivuilla");
        }
    }

    @Test
    void bellOpensTheList_withFollowerAndNarrausMessage_andMarksThemRead() {
        AppUser me = TestData.newUser(users, "Minä");
        AppUser fan = TestData.newUser(users, "Fani");
        users.toggleFollow(fan, me);
        system.send(admin(), Set.of(me), "Huoltokatko huomenna");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(FeedView.class);

            window.test(bell(window)).click();

            Dialog dialog = window.find(Dialog.class).all().stream().filter(Dialog::isOpened).findFirst().orElseThrow();
            String text = dialog.getElement().getTextRecursively();
            assertTrue(text.contains(fan.getDisplayName() + " alkoi seurata sinua"), "Uusi seuraaja: " + text);
            assertTrue(text.contains("Narraus: Huoltokatko huomenna"), "Narraus-viesti listassa");
            assertEquals("", badgeText(bell(window)), "Merkki poistuu, kun lista on avattu");
            assertEquals(1, system.messagesFor(me).size(), "Narraus-viesti on silti Keskusteluissa");
        }
    }

    @Test
    void emptyList_saysSo() {
        AppUser me = TestData.newUser(users, "Minä");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(FeedView.class);
            window.test(bell(window)).click();

            assertTrue(window.find(Dialog.class).all().stream().filter(Dialog::isOpened)
                    .anyMatch(d -> d.getElement().getTextRecursively().contains("Ei ilmoituksia.")));
        }
    }

    @Test
    void describe_formatsEachKind() {
        AppUser fan = TestData.newUser(users, "Fani");
        var follower = new com.narraus.domain.NotificationService.Entry(com.narraus.domain.NotificationType.FOLLOWER,
                false, fan, fan.getId(), null, java.time.Instant.now(), true);
        var like = new com.narraus.domain.NotificationService.Entry(com.narraus.domain.NotificationType.LIKE, false,
                fan, 5L, "Hauki", java.time.Instant.now(), true);
        var event = new com.narraus.domain.NotificationService.Entry(com.narraus.domain.NotificationType.EVENT, false,
                fan, 7L, "Syyskisa", java.time.Instant.now(), true);

        assertEquals(fan.getDisplayName() + " alkoi seurata sinua", NotificationBell.describe(follower));
        assertEquals(fan.getDisplayName() + " tykkäsi julkaisustasi: Hauki", NotificationBell.describe(like));
        assertEquals("Uusi kilpailu: Syyskisa", NotificationBell.describe(event));
    }

    // ---- ylläpitäjän pikapainikkeet kotisivulla ----

    @Test
    void admin_hasShortcutButtonsOnTheHomePage_ordinaryUsersDoNot() {
        admin();
        AppUser normal = TestData.newUser(users, "Tavallinen");

        try (var adminUser = app.newUser("robbe", "USER"); var normalUser = app.newUser(normal.getUsername(), "USER")) {
            var adminWindow = adminUser.newWindow();
            adminWindow.navigate(FeedView.class);
            assertTrue(adminWindow.find(Div.class).all().stream().anyMatch(d -> d.hasClassName("admin-shortcuts")));
            assertTrue(adminWindow.find(Button.class).all().stream().anyMatch(b -> "Ylläpito".equals(b.getText())));
            assertTrue(adminWindow.find(Button.class).all().stream()
                    .anyMatch(b -> "Lähetä Narraus-viesti".equals(b.getText())));

            adminWindow.findButton().withText("Lähetä Narraus-viesti").click();
            assertTrue(adminWindow.find(Dialog.class).all().stream().anyMatch(Dialog::isOpened), "Lähetysikkuna aukeaa");

            var normalWindow = normalUser.newWindow();
            normalWindow.navigate(FeedView.class);
            assertTrue(normalWindow.find(Div.class).all().stream().noneMatch(d -> d.hasClassName("admin-shortcuts")));
        }
    }

    @Test
    void adminShortcut_opensTheAdminPage() {
        admin();

        try (var adminUser = app.newUser("robbe", "USER")) {
            var window = adminUser.newWindow();
            window.navigate(FeedView.class);

            window.findButton().withText("Ylläpito").click();

            assertEquals(AdminView.class, window.getCurrentView().getClass());
        }
    }

    // ---- käyttöehdot ja tietosuoja ----

    @Test
    void termsAndPrivacy_areOnTheOwnAccountPage_forEveryUser_inFinnish() {
        AppUser me = TestData.newUser(users, "Minä");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(ProfileView.class);
            assertTrue(window.find(Button.class).all().stream().anyMatch(b -> "Käyttöehdot ja tietosuoja".equals(b.getText())));
            assertTrue(window.find(com.vaadin.flow.component.Html.class).all().isEmpty(),
                    "Teksti ladataan vasta, kun osio avataan");

            TestData.openSection(window, "Käyttöehdot ja tietosuoja");

            String text = window.find(com.vaadin.flow.component.Html.class).single().getInnerHtml();
            for (String needed : new String[]{"Käyttöehdot", "Tietosuojaseloste", "Rekisterinpitäjä", "MapTiler",
                    "Poista tili", "tietosuojavaltuutetun", "evästeet", "salasana"}) {
                assertTrue(text.toLowerCase().contains(needed.toLowerCase()), "Tekstistä puuttuu: " + needed);
            }
            assertTrue(text.contains("Päivitetty"), "Päivämäärä näkyy");
        }
    }

    @Test
    void legalText_isInTheClasspath_andMentionsWhatTheAppReallyStores() throws Exception {
        try (var in = NotificationBellTest.class.getResourceAsStream("/legal/kayttoehdot-ja-tietosuoja.html")) {
            String html = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            for (String topic : new String[]{"push", "koordinaat", "varmuuskopio", "90 päivää", "suolattuna", "alle 13"}) {
                assertTrue(html.contains(topic), "Tietosuojaselosteesta puuttuu: " + topic);
            }
            assertTrue(html.indexOf("<div class=\"legal\">") == 0, "Yksi juurielementti (Html-komponentti vaatii sen)");
        }
    }
}
