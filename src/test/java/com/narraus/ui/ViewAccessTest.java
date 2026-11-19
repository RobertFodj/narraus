package com.narraus.ui;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.narraus.NarrausTest;
import com.narraus.domain.UserService;
import com.vaadin.browserless.SecuredBrowserlessApplicationContext;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;

@NarrausTest
class ViewAccessTest {

    @Autowired
    private SecuredBrowserlessApplicationContext<Authentication> app;

    @Autowired
    private UserService users;

    @Test
    void anonymousUser_rootRoute_redirectsToLogin() {
        try (var anonymous = app.newUser()) {
            var window = anonymous.newWindow();
            window.navigate("", LoginView.class);
        }
    }

    @Test
    void anonymousUser_protectedViews_redirectToLogin() {
        try (var anonymous = app.newUser()) {
            var window = anonymous.newWindow();
            window.navigate("people", LoginView.class);
            window.navigate("chat", LoginView.class);
        }
    }

    @Test
    void anonymousUser_registerView_isAvailable() {
        try (var anonymous = app.newUser()) {
            var window = anonymous.newWindow();
            window.navigate(RegisterView.class);
            assertInstanceOf(RegisterView.class, window.getCurrentView());
        }
    }

    /**
     * Regressio: kun MainLayoutilta puuttui @PermitAll, kirjautunut käyttäjä näki
     * kirjautumisen jälkeen Vaadinin reittilistan eikä etusivua.
     */
    @Test
    void loggedInUser_canOpenEveryViewInMainLayout() {
        String username = "kalastaja-" + UUID.randomUUID().toString().substring(0, 8);
        users.register(username, "Kalle", "salasana123");

        try (var user = app.newUser(username, "USER")) {
            var window = user.newWindow();
            window.navigate(FeedView.class);
            window.navigate(PeopleView.class);
            window.navigate(ChatView.class);
        }
    }
}
