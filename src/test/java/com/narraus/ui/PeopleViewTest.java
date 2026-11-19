package com.narraus.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.narraus.NarrausTest;
import com.narraus.domain.AppUser;
import com.narraus.domain.ChatService;
import com.narraus.domain.UserService;
import com.vaadin.browserless.BrowserlessUIContext;
import com.vaadin.browserless.SecuredBrowserlessApplicationContext;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.TextField;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;

@NarrausTest
class PeopleViewTest {

    @Autowired
    private SecuredBrowserlessApplicationContext<Authentication> app;

    @Autowired
    private UserService users;

    @Autowired
    private ChatService chats;

    private void search(BrowserlessUIContext window, String query) {
        TextField field = window.find(TextField.class).single();
        window.test(field).setValue(query);
    }

    private boolean hasText(BrowserlessUIContext window, String text) {
        return window.find(Span.class).all().stream().anyMatch(s -> s.getText().contains(text));
    }

    @Test
    void search_showsMatchingUser_butNeverTheLoggedInUser() {
        AppUser me = TestData.newUser(users, "Etsija");
        AppUser target = TestData.newUser(users, "Kohde");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(PeopleView.class);

            search(window, target.getDisplayName());
            assertTrue(hasText(window, target.getDisplayName() + " (@" + target.getUsername() + ")"),
                    "Haettu käyttäjä pitäisi näkyä tuloksissa");

            search(window, me.getDisplayName());
            assertTrue(hasText(window, "Ketään ei löytynyt."), "Omaa itseä ei listata");
        }
    }

    @Test
    void search_withNoMatches_showsEmptyMessage() {
        AppUser me = TestData.newUser(users, "Etsija");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(PeopleView.class);

            search(window, TestData.unique("ei-olemassa"));
            assertTrue(hasText(window, "Ketään ei löytynyt."));
        }
    }

    @Test
    void followButton_togglesFollowing() {
        AppUser me = TestData.newUser(users, "Seuraaja");
        AppUser target = TestData.newUser(users, "Seurattava");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(PeopleView.class);
            search(window, target.getDisplayName());

            window.findButton().withText("Seuraa").click();
            assertTrue(users.isFollowing(me, target), "Seuraaminen pitäisi tallentua");
            assertTrue(window.find(Button.class).all().stream()
                            .anyMatch(b -> "Lopeta seuraaminen".equals(b.getText())),
                    "Napin teksti pitäisi vaihtua");
            assertEquals(1, users.followerCount(target));

            window.findButton().withText("Lopeta seuraaminen").click();
            assertFalse(users.isFollowing(me, target), "Seuraaminen pitäisi päättyä");
            assertEquals(0, users.followerCount(target));
        }
    }

    @Test
    void messageButton_opensDirectConversation_inChatView() {
        AppUser me = TestData.newUser(users, "Viestija");
        AppUser target = TestData.newUser(users, "Vastaanottaja");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(PeopleView.class);
            search(window, target.getDisplayName());

            window.findButton().withText("Viesti").click();

            assertInstanceOf(ChatView.class, window.getCurrentView());
            var conversations = chats.conversationsOf(me);
            assertEquals(1, conversations.size());
            assertEquals(target.getDisplayName(), conversations.get(0).titleFor(me));
        }
    }
}
