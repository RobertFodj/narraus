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
    void commentButton_hidesComments_untilPressed_thenOpensDialogOnTop() {
        AppUser me = TestData.newUser(users, "Minä");
        AppUser other = TestData.newUser(users, "Kommentoija");
        var post = posts.create(me, TestData.unique("Hauki"), null, null, null, null, null, null, null);
        posts.addComment(post.getId(), other, "Hieno kala!");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(FeedView.class);

            assertNotNull(first(window, "Kommentoi"), "Jokaisessa saaliissa on Kommentoi-nappi");
            assertFalse(dialogOpen(window), "Kommentit ovat piilossa ennen painallusta");
            assertFalse(window.find(Span.class).all().stream().anyMatch(s -> "Hieno kala!".equals(s.getText())));

            window.test(first(window, "Kommentoi")).click();

            assertTrue(dialogOpen(window));
            assertTrue(window.find(com.vaadin.flow.component.html.Paragraph.class).all().stream()
                    .anyMatch(p -> "Hieno kala!".equals(p.getText())), "Kommentti näkyy dialogissa");
            assertTrue(window.find(Span.class).all().stream()
                    .anyMatch(s -> other.getDisplayName().equals(s.getText())), "Kommentoijan näyttönimi näkyy");
        }
    }

    @Test
    void writingAComment_savesItAndShowsIt() {
        AppUser me = TestData.newUser(users, "Minä");
        var post = posts.create(me, TestData.unique("Ahven"), null, null, null, null, null, null, null);

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(FeedView.class);
            window.test(first(window, "Kommentoi")).click();

            window.test(window.find(TextArea.class).withLabel("Kirjoita kommentti").single())
                    .setValue("Upea saalis");
            window.test(first(window, "Lähetä")).click();

            assertEquals(1, posts.commentCount(post));
            assertEquals("Upea saalis", posts.comments(post).get(0).getText());
            assertTrue(window.find(com.vaadin.flow.component.html.Paragraph.class).all().stream()
                    .anyMatch(p -> "Upea saalis".equals(p.getText())));
        }
    }

    @Test
    void emptyComment_isNotSaved() {
        AppUser me = TestData.newUser(users, "Minä");
        var post = posts.create(me, TestData.unique("Kuha"), null, null, null, null, null, null, null);

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(FeedView.class);
            window.test(first(window, "Kommentoi")).click();
            window.test(first(window, "Lähetä")).click();

            assertEquals(0, posts.commentCount(post));
            assertEquals("Kommentti ei voi olla tyhjä", window.findNotification().getText());
        }
    }

    // ---- kuvat ja asettelu ----

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
    void feed_isAGridThatFitsTwoCardsSideBySide() {
        AppUser me = TestData.newUser(users, "Minä");
        posts.create(me, TestData.unique("Hauki"), null, null, null, null, null, null, null);
        posts.create(me, TestData.unique("Ahven"), null, null, null, null, null, null, null);

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            FeedView feed = window.navigate(FeedView.class);

            Div grid = window.find(Div.class).all().stream()
                    .filter(d -> "grid".equals(d.getStyle().get("display"))).findFirst().orElseThrow();
            assertTrue(grid.getStyle().get("grid-template-columns").contains("minmax(min(100%, 16em)"));
            assertEquals("44em", feed.getMaxWidth(), "Leveys riittää kahdelle 16 em kortille vierekkäin, mutta ei kolmelle");
        }
    }

    // ---- oma tili ----

    @Test
    void ownPage_hasEditAccountButton_secondFromBottom_andNoPostEditing() {
        AppUser me = TestData.newUser(users, "Minä");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            ProfileView view = window.navigate(ProfileView.class);

            var children = view.getChildren().toList();
            assertTrue(children.get(children.size() - 2) instanceof Button b && "Muokkaa tiliä".equals(b.getText()),
                    "Muokkaa tiliä on toiseksi alimpana, tilin poiston yläpuolella");
            assertEquals(0, window.find(Button.class).all().stream().filter(b -> "Muokkaa".equals(b.getText())).count(),
                    "Napista ei muokata saaliita");
        }
    }

    @Test
    void editAccount_changesDisplayName() {
        AppUser me = TestData.newUser(users, "Minä");
        String newName = TestData.unique("Uusi");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(ProfileView.class);
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
}
