package com.narraus.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.narraus.NarrausTest;
import com.narraus.domain.AppUser;
import com.narraus.domain.PostService;
import com.narraus.domain.UserService;
import com.vaadin.browserless.BrowserlessUIContext;
import com.vaadin.browserless.SecuredBrowserlessApplicationContext;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;

/** Kommentit, pienet kuvat, kahden sarakkeen syöte ja tilin muokkaus. */
@NarrausTest
class CommentsAndLayoutTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3, 4};

    @Autowired
    private SecuredBrowserlessApplicationContext<Authentication> app;
    @Autowired
    private UserService users;
    @Autowired
    private PostService posts;

    private static boolean dialogOpen(BrowserlessUIContext w) {
        return w.find(Dialog.class).all().stream().anyMatch(Dialog::isOpened);
    }

    private static Button first(BrowserlessUIContext w, String text) {
        return w.find(Button.class).all().stream().filter(b -> text.equals(b.getText())).findFirst().orElseThrow();
    }

    // ---- kommentit ----

    @Test
    void card_hasNoCommentButton() {
        AppUser me = TestData.newUser(users, "Minä");
        posts.create(me, TestData.unique("Hauki"), null, null, null, null, null, null, null);

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(FeedView.class);

            assertTrue(window.find(Button.class).all().stream().noneMatch(b -> "Kommentoi".equals(b.getText())));
        }
    }

    @Test
    void photo_isSmallInTheCard_andOpensLargeWhenClicked() {
        AppUser me = TestData.newUser(users, "Minä");
        posts.create(me, TestData.unique("Hauki"), null, null, null, null, null, PNG, "image/png");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(FeedView.class);

            Image thumb = window.find(Image.class).all().get(0);
            assertEquals("10em", thumb.getStyle().get("max-height"), "Kortin kuva on pieni");
            assertEquals("contain", thumb.getStyle().get("object-fit"), "Kuvaa ei rajata, se säilyttää muotonsa");
            assertEquals("auto", thumb.getStyle().get("width"));
            assertEquals("auto", thumb.getHeight(), "Kiinteää korkeutta ei ole, korkeus määräytyy kuvan mukaan");
            assertFalse(dialogOpen(window));

            window.test(thumb).click();

            assertTrue(dialogOpen(window), "Klikkaus avaa kuvan isona");
            assertTrue(window.find(Dialog.class).all().stream()
                    .anyMatch(d -> d.isOpened() && d.getClassNames().contains("photo-dialog")));
        }
    }

    @Test
    void feed_isAGridThatFitsTwoCardsSideBySide() throws Exception {
        AppUser me = TestData.newUser(users, "Minä");
        posts.create(me, TestData.unique("Hauki"), null, null, null, null, null, null, null);
        posts.create(me, TestData.unique("Ahven"), null, null, null, null, null, null, null);

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            FeedView feed = window.navigate(FeedView.class);

            assertTrue(window.find(Div.class).all().stream().anyMatch(d -> d.hasClassName("feed-list")));
            String css;
            try (var in = CommentsAndLayoutTest.class.getResourceAsStream("/META-INF/resources/styles.css")) {
                css = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
            assertTrue(css.contains("minmax(min(100%, 16em), 1fr)"), "Leveällä näytöllä auto-fit-ruudukko");
            assertTrue(css.contains("align-items: stretch"), "Rivin kortit ovat saman korkuisia");
            assertTrue(css.contains(".catch-card .catch-like"), "Tykkäysnappi kortin alareunaan");
            assertTrue(css.contains("repeat(2, minmax(0, 1fr))"), "Puhelimella aina kaksi saraketta");
            assertEquals("44em", feed.getMaxWidth(), "Leveys riittää kahdelle 16 em kortille vierekkäin, mutta ei kolmelle");
        }
    }

    // ---- oma tili ----

    @Test
    void ownPage_hasSectionButtons_andOpensAccountEditingWithEditAndDeleteButtons() {
        AppUser me = TestData.newUser(users, "Minä");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(ProfileView.class);

            for (String label : new String[]{"Ilmoitukset", "Omat julkaisut", "Seuraajat", "Tilin muokkaus"}) {
                assertNotNull(first(window, label), "Nappi " + label);
            }
            assertEquals(0, window.find(Button.class).all().stream().filter(b -> "Muokkaa tiliä".equals(b.getText())).count(),
                    "Osiot ovat suljettuina, kunnes napista painetaan");

            TestData.openSection(window, "Tilin muokkaus");

            assertNotNull(first(window, "Muokkaa tiliä"));
            assertNotNull(first(window, "Poista tili…"));
            assertEquals(0, window.find(Button.class).all().stream().filter(b -> "Muokkaa".equals(b.getText())).count(),
                    "Napista ei muokata saaliita");

            TestData.openSection(window, "Tilin muokkaus"); // uusi painallus sulkee osion
            assertEquals(0, window.find(Button.class).all().stream().filter(b -> "Poista tili…".equals(b.getText())).count());
        }
    }

    @Test
    void followersSection_listsWhoFollowsYou_andWhomYouFollow() {
        AppUser me = TestData.newUser(users, "Minä");
        AppUser fan = TestData.newUser(users, "Fani");
        AppUser idol = TestData.newUser(users, "Idoli");
        users.toggleFollow(fan, me);
        users.toggleFollow(me, idol);

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(ProfileView.class);
            TestData.openSection(window, "Seuraajat");

            var names = window.find(Button.class).all().stream().filter(b -> b.hasClassName("profile-user"))
                    .map(Button::getText).toList();
            assertTrue(names.contains(fan.getDisplayName()), "Seuraaja näkyy");
            assertTrue(names.contains(idol.getDisplayName()), "Seurattava näkyy");
            assertEquals(2, names.size());

            window.test(window.find(Button.class).all().stream()
                    .filter(b -> idol.getDisplayName().equals(b.getText())).findFirst().orElseThrow()).click();
            assertEquals(UserProfileView.class, window.getCurrentView().getClass(), "Nimestä avautuu käyttäjän sivu");
        }
    }

    @Test
    void editAccount_changesDisplayName() {
        AppUser me = TestData.newUser(users, "Minä");
        String newName = TestData.unique("Uusi");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(ProfileView.class);
            TestData.openSection(window, "Tilin muokkaus");
            window.test(first(window, "Muokkaa tiliä")).click();

            TextField name = window.find(TextField.class).withLabel("Näyttönimi").single();
            assertEquals(me.getDisplayName(), name.getValue(), "Kentät ovat valmiiksi täytetty");
            window.test(name).setValue(newName);
            window.test(first(window, "Tallenna")).click();

            assertEquals(newName, users.find(me.getUsername()).orElseThrow().getDisplayName());
            assertFalse(dialogOpen(window));
            assertTrue(window.find(Span.class).all().stream().anyMatch(s -> ("Näyttönimi: " + newName).equals(s.getText())));
        }
    }

    @Test
    void editAccount_wrongCurrentPassword_orMismatch_isRejected() {
        AppUser me = TestData.newUser(users, "Minä");
        String hash = users.find(me.getUsername()).orElseThrow().getPasswordHash();

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(ProfileView.class);
            TestData.openSection(window, "Tilin muokkaus");
            window.test(first(window, "Muokkaa tiliä")).click();

            window.test(window.find(PasswordField.class).withLabel("Nykyinen salasana").single()).setValue("vaara");
            window.test(window.find(PasswordField.class).withLabel("Uusi salasana (väh. 8 merkkiä)").single())
                    .setValue("uusisalasana1");
            window.test(window.find(PasswordField.class).withLabel("Uusi salasana uudelleen").single())
                    .setValue("uusisalasana2");
            window.test(first(window, "Tallenna")).click();
            assertEquals("Uudet salasanat eivät täsmää", window.findNotification().getText());

            window.test(window.find(PasswordField.class).withLabel("Uusi salasana uudelleen").single())
                    .setValue("uusisalasana1");
            window.test(first(window, "Tallenna")).click();
            assertEquals(hash, users.find(me.getUsername()).orElseThrow().getPasswordHash());
            assertTrue(dialogOpen(window), "Dialogi jää auki");
        }
    }

    @Test
    void editAccount_changesPassword_withTheCurrentOne() {
        AppUser me = TestData.newUser(users, "Minä");
        String hash = users.find(me.getUsername()).orElseThrow().getPasswordHash();

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(ProfileView.class);
            TestData.openSection(window, "Tilin muokkaus");
            window.test(first(window, "Muokkaa tiliä")).click();

            window.test(window.find(PasswordField.class).withLabel("Nykyinen salasana").single()).setValue("salasana123");
            window.test(window.find(PasswordField.class).withLabel("Uusi salasana (väh. 8 merkkiä)").single())
                    .setValue("uusisalasana1");
            window.test(window.find(PasswordField.class).withLabel("Uusi salasana uudelleen").single())
                    .setValue("uusisalasana1");
            window.test(first(window, "Tallenna")).click();

            assertTrue(!hash.equals(users.find(me.getUsername()).orElseThrow().getPasswordHash()));
            assertFalse(dialogOpen(window));
        }
    }

    @Test
    void editAccount_changesUsername_withTheCurrentPassword_andLogsOut() {
        AppUser me = TestData.newUser(users, "Minä");
        String newUsername = "u-" + TestData.unique("uusi").toLowerCase();

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(ProfileView.class);
            TestData.openSection(window, "Tilin muokkaus");
            window.test(first(window, "Muokkaa tiliä")).click();

            TextField username = window.find(TextField.class).withLabel("Käyttäjätunnus").single();
            assertEquals(me.getUsername(), username.getValue(), "Kenttä on valmiiksi täytetty");
            window.test(username).setValue(newUsername);

            window.test(first(window, "Tallenna")).click(); // ilman nykyistä salasanaa
            assertTrue(users.find(me.getUsername()).isPresent(), "Tunnus ei vaihdu ilman salasanaa");
            assertTrue(users.find(newUsername).isEmpty());

            window.test(window.find(PasswordField.class).withLabel("Nykyinen salasana").single()).setValue("salasana123");
            try {
                window.test(first(window, "Tallenna")).click();
            } catch (RuntimeException logoutNeedsRealRequest) {
                // Uloskirjautuminen tarvitsee oikean selainpyynnön; tulos tarkistetaan tietokannasta
            }

            assertTrue(users.find(newUsername).isPresent(), "Uusi tunnus on käytössä");
            assertTrue(users.find(me.getUsername()).isEmpty(), "Vanha tunnus vapautuu");
            assertEquals(me.getId(), users.find(newUsername).orElseThrow().getId(), "Sama tili, tiedot säilyvät");
        }
    }

    @Test
    void username_cannotBeTakenOrTooShort() {
        AppUser me = TestData.newUser(users, "Minä");
        AppUser other = TestData.newUser(users, "Toinen");

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () ->
                users.updateAccount(me.getId(), me.getDisplayName(), other.getUsername().toUpperCase(), "salasana123", ""));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () ->
                users.updateAccount(me.getId(), me.getDisplayName(), "ab", "salasana123", ""));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () ->
                users.updateAccount(me.getId(), me.getDisplayName(), "uusi-tunnus-x", "vaara", ""));
        assertEquals(me.getUsername(), users.find(me.getUsername()).orElseThrow().getUsername(), "Mitään ei muutu");
        assertFalse(users.updateAccount(me.getId(), "Pelkkä nimi", me.getUsername(), "", ""),
                "Pelkkä nimenvaihto ei vaadi salasanaa eikä vaihda tunnusta");
    }

    @Test
    void chatListHeader_staysInsideItsIsland() throws Exception {
        String css;
        try (var in = CommentsAndLayoutTest.class.getResourceAsStream("/META-INF/resources/styles.css")) {
            css = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        int rule = css.indexOf(".chat-view .chat-list > .island {");
        assertTrue(rule >= 0 && css.substring(rule, css.indexOf('}', rule)).contains("flex-wrap: wrap"),
                "Keskustelulistan otsikkosaaren nappi ei leikkaudu");
    }
}
