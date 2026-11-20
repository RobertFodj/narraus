package com.narraus.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.narraus.NarrausTest;
import com.narraus.domain.AppUser;
import com.narraus.domain.PostService;
import com.narraus.domain.UserService;
import com.vaadin.browserless.BrowserlessUIContext;
import com.vaadin.browserless.SecuredBrowserlessApplicationContext;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.textfield.PasswordField;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;

/** Tilin poisto omalta sivulta. */
@NarrausTest
class AccountDeletionViewTest {

    @Autowired
    private SecuredBrowserlessApplicationContext<Authentication> app;
    @Autowired
    private UserService users;
    @Autowired
    private PostService posts;

    private static boolean hasButton(BrowserlessUIContext window, String text) {
        return window.find(Button.class).all().stream().anyMatch(b -> text.equals(b.getText()));
    }

    private static boolean openDialog(BrowserlessUIContext window) {
        return window.find(Dialog.class).all().stream().anyMatch(Dialog::isOpened);
    }

    @Test
    void ownPage_hasDeleteAccountSection_withWarning() {
        AppUser me = TestData.newUser(users, "Minä");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(ProfileView.class);

            assertTrue(window.find(H3.class).all().stream().anyMatch(h -> "Poista tili".equals(h.getText())));
            assertTrue(hasButton(window, "Poista tili…"));
            assertTrue(window.find(com.vaadin.flow.component.html.Paragraph.class).all().stream()
                    .anyMatch(p -> p.getText().contains("pysyvä") && p.getText().contains("voi perua")));
        }
    }

    @Test
    void otherUsersPage_hasNoDeleteAccountButton() {
        AppUser me = TestData.newUser(users, "Katsoja");
        AppUser other = TestData.newUser(users, "Toinen");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate("user/" + other.getId(), UserProfileView.class);

            assertFalse(hasButton(window, "Poista tili…"));
        }
    }

    @Test
    void deleteButton_opensDialogAskingForPassword() {
        AppUser me = TestData.newUser(users, "Minä");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(ProfileView.class);

            window.findButton().withText("Poista tili…").click();

            assertTrue(openDialog(window));
            assertEquals(1, window.find(PasswordField.class).withLabel("Vahvista salasanalla").all().size());
            assertTrue(hasButton(window, "Poista tili pysyvästi"));
        }
    }

    @Test
    void wrongPassword_showsError_andKeepsTheAccount() {
        AppUser me = TestData.newUser(users, "Minä");
        posts.create(me, TestData.unique("Hauki"), null, null, null, null, null, null, null);

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(ProfileView.class);
            window.findButton().withText("Poista tili…").click();

            window.test(window.find(PasswordField.class).withLabel("Vahvista salasanalla").single())
                    .setValue("vaara-salasana");
            window.findButton().withText("Poista tili pysyvästi").click();

            assertEquals("Väärä salasana", window.findNotification().getText());
            assertTrue(users.find(me.getUsername()).isPresent(), "Tili säilyy väärällä salasanalla");
            assertEquals(1, posts.postCount(me));
            assertTrue(openDialog(window), "Dialogi jää auki, jotta voi yrittää uudelleen");
        }
    }

    @Test
    void emptyPassword_isRejected() {
        AppUser me = TestData.newUser(users, "Minä");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(ProfileView.class);
            window.findButton().withText("Poista tili…").click();

            window.findButton().withText("Poista tili pysyvästi").click();

            assertEquals("Väärä salasana", window.findNotification().getText());
            assertTrue(users.find(me.getUsername()).isPresent());
        }
    }

    @Test
    void cancel_keepsTheAccount() {
        AppUser me = TestData.newUser(users, "Minä");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(ProfileView.class);
            window.findButton().withText("Poista tili…").click();

            window.findButton().withText("Peruuta").click();

            assertTrue(users.find(me.getUsername()).isPresent());
            assertFalse(openDialog(window));
        }
    }

    @Test
    void correctPassword_deletesTheAccountAndItsData() {
        AppUser me = TestData.newUser(users, "Minä");
        posts.create(me, TestData.unique("Hauki"), null, null, null, null, null, null, null);

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(ProfileView.class);
            window.findButton().withText("Poista tili…").click();
            window.test(window.find(PasswordField.class).withLabel("Vahvista salasanalla").single())
                    .setValue("salasana123");

            try {
                window.findButton().withText("Poista tili pysyvästi").click();
            } catch (RuntimeException logoutNeedsRealRequest) {
                // Uloskirjautuminen tarvitsee oikean selainpyynnön, jota selaimeton testi ei tee.
                // Tilin poisto tehdään ennen sitä, joten tulos tarkistetaan alla.
            }

            assertTrue(users.find(me.getUsername()).isEmpty(), "Tili on poistettu");
            assertEquals(0, posts.byAuthor(me).size(), "Julkaisut on poistettu");
        }
    }
}
