package com.narraus.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.narraus.NarrausTest;
import com.narraus.domain.AppUser;
import com.narraus.domain.UserService;
import com.vaadin.browserless.SecuredBrowserlessApplicationContext;
import com.vaadin.flow.component.button.Button;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;

/** Kielivalinta: kirjautumissivun yläkulma ja Oma tili; oletuskieli suomi. */
@NarrausTest
class LanguageMenuTest {

    @Autowired
    private SecuredBrowserlessApplicationContext<Authentication> app;
    @Autowired
    private UserService users;

    @Test
    void loginAndRegisterPages_haveALanguageButtonInTheCorner_beforeLoggingIn() {
        try (var anonymous = app.newUser()) {
            var window = anonymous.newWindow();
            window.navigate(LoginView.class);
            assertTrue(window.find(Button.class).all().stream().anyMatch(b -> b.hasClassName("language-menu")),
                    "Kielivalinta kirjautumissivulla");

            window.navigate(RegisterView.class);
            assertTrue(window.find(Button.class).all().stream().anyMatch(b -> b.hasClassName("language-menu")),
                    "Kielivalinta rekisteröitymissivulla");
        }
    }

    @Test
    void ownAccount_hasALanguageButton_showingTheCurrentLanguage_firstFinnish() {
        AppUser me = TestData.newUser(users, "Minä");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(ProfileView.class);

            Button language = window.find(Button.class).all().stream()
                    .filter(b -> b.hasClassName("language-menu-labeled")).findFirst().orElseThrow();
            assertEquals("Kieli", language.getText());
            assertTrue(language.getElement().getChildren().anyMatch(c -> "Suomi".equals(c.getText())),
                    "Ensimmäisellä kerralla kieli on suomi");
        }
    }

    @Test
    void currentLanguage_followsTheUiLocale() {
        AppUser me = TestData.newUser(users, "Minä");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(ProfileView.class);

            assertEquals(Language.FI, LanguageMenu.currentLanguage());
            window.getUI().setLocale(Language.NO.locale());
            assertEquals(Language.NO, LanguageMenu.currentLanguage(), "nb-kieli tunnistetaan norjaksi");
            window.getUI().setLocale(Language.SV.locale());
            assertEquals(Language.SV, LanguageMenu.currentLanguage());
        }
    }
}
