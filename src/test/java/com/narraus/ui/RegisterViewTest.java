package com.narraus.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.narraus.NarrausTest;
import com.narraus.domain.UserService;
import com.vaadin.browserless.BrowserlessUIContext;
import com.vaadin.browserless.SecuredBrowserlessApplicationContext;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;

@NarrausTest
class RegisterViewTest {

    @Autowired
    private SecuredBrowserlessApplicationContext<Authentication> app;

    @Autowired
    private UserService users;

    private static String uniqueName() {
        return "uusi-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private void fillForm(BrowserlessUIContext window, String username, String password, String confirm) {
        window.findTextField().withLabel("Käyttäjätunnus").setValue(username);
        window.findTextField().withLabel("Näytettävä nimi").setValue("Uusi Käyttäjä");
        window.findPasswordField().withLabel("Salasana (väh. 8 merkkiä)").setValue(password);
        window.findPasswordField().withLabel("Salasana uudelleen").setValue(confirm);
    }

    @Test
    void validForm_createsUser_andNavigatesToLogin() {
        String username = uniqueName();
        try (var anonymous = app.newUser()) {
            var window = anonymous.newWindow();
            window.navigate(RegisterView.class);

            fillForm(window, username, "salasana123", "salasana123");
            window.findButton().withText("Luo tunnus").click();

            assertTrue(users.find(username).isPresent(), "Käyttäjän pitäisi löytyä tietokannasta");
            assertInstanceOf(LoginView.class, window.getCurrentView());
        }
    }

    @Test
    void mismatchingPasswords_showError_andCreateNoUser() {
        String username = uniqueName();
        try (var anonymous = app.newUser()) {
            var window = anonymous.newWindow();
            window.navigate(RegisterView.class);

            fillForm(window, username, "salasana123", "toinen-salasana");
            window.findButton().withText("Luo tunnus").click();

            assertEquals("Salasanat eivät täsmää", window.findNotification().getText());
            assertFalse(users.find(username).isPresent());
            assertInstanceOf(RegisterView.class, window.getCurrentView());
        }
    }

    @Test
    void tooShortPassword_showsServiceError() {
        String username = uniqueName();
        try (var anonymous = app.newUser()) {
            var window = anonymous.newWindow();
            window.navigate(RegisterView.class);

            fillForm(window, username, "lyhyt", "lyhyt");
            window.findButton().withText("Luo tunnus").click();

            assertEquals("Salasanan on oltava vähintään 8 merkkiä", window.findNotification().getText());
            assertFalse(users.find(username).isPresent());
        }
    }
}
