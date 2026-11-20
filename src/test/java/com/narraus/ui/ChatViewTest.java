package com.narraus.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.narraus.NarrausTest;
import com.narraus.domain.AppUser;
import com.narraus.domain.ChatService;
import com.narraus.domain.Conversation;
import com.narraus.domain.UserService;
import com.vaadin.browserless.BrowserlessUIContext;
import com.vaadin.browserless.SecuredBrowserlessApplicationContext;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.messages.MessageInput;
import com.vaadin.flow.component.messages.MessageList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;

@NarrausTest
class ChatViewTest {

    @Autowired
    private SecuredBrowserlessApplicationContext<Authentication> app;

    @Autowired
    private UserService users;

    @Autowired
    private ChatService chats;

    private static boolean hasTitle(BrowserlessUIContext window, String text) {
        return window.find(H3.class).all().stream().anyMatch(h -> text.equals(h.getText()));
    }

    private static boolean hasButton(BrowserlessUIContext window, String text) {
        return window.find(Button.class).all().stream().anyMatch(b -> text.equals(b.getText()));
    }

    private static int messageCount(BrowserlessUIContext window) {
        return window.test(window.find(MessageList.class).single()).size();
    }

    @Test
    void userWithoutConversations_seesHint_andDisabledInput() {
        AppUser me = TestData.newUser(users, "Yksinainen");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(ChatView.class);

            assertTrue(window.find(Span.class).all().stream()
                    .anyMatch(s -> s.getText().startsWith("Ei keskusteluja")));
            assertTrue(hasTitle(window, "Valitse keskustelu"));
            assertFalse(window.find(MessageInput.class).single().isEnabled());
        }
    }

    @Test
    void openingConversation_andSendingMessage_showsAndStoresIt() {
        AppUser me = TestData.newUser(users, "Lahettaja");
        AppUser friend = TestData.newUser(users, "Kaveri");
        Conversation conversation = chats.openDirect(me, friend);

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(ChatView.class);

            window.findButton().withText(friend.getDisplayName()).click();
            assertTrue(hasTitle(window, friend.getDisplayName()));
            assertTrue(window.find(MessageInput.class).single().isEnabled());

            window.test(window.find(MessageInput.class).single()).send("Moi kalakaveri");

            assertEquals(1, messageCount(window));
            var stored = chats.messages(conversation, me);
            assertEquals(1, stored.size());
            assertEquals("Moi kalakaveri", stored.get(0).getText());
            assertEquals(me, stored.get(0).getSender());
        }
    }

    @Test
    void messageFromOtherUser_appearsInOpenConversation() {
        AppUser me = TestData.newUser(users, "Lukija");
        AppUser friend = TestData.newUser(users, "Kirjoittaja");
        Conversation conversation = chats.openDirect(me, friend);

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate("chat?c=" + conversation.getId(), ChatView.class);
            assertEquals(0, messageCount(window));

            chats.send(conversation, friend, "Hei takaisin");

            assertEquals(1, messageCount(window), "Broadcaster pitäisi päivittää avoin keskustelu");
        }
    }

    @Test
    void openingConversationByQueryParameter_selectsIt() {
        AppUser me = TestData.newUser(users, "Avaaja");
        AppUser friend = TestData.newUser(users, "Toinen");
        Conversation conversation = chats.openDirect(me, friend);

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate("chat?c=" + conversation.getId(), ChatView.class);

            assertTrue(hasTitle(window, friend.getDisplayName()));
            assertTrue(window.find(MessageInput.class).single().isEnabled());
        }
    }

    /** Tietoturva: urlin c-parametrilla ei voi avata keskustelua, jonka jäsen ei ole. */
    @Test
    void conversationOfOthers_cannotBeOpenedByQueryParameter() {
        AppUser me = TestData.newUser(users, "Ulkopuolinen");
        AppUser a = TestData.newUser(users, "A");
        AppUser b = TestData.newUser(users, "B");
        Conversation others = chats.openDirect(a, b);
        chats.send(others, a, "Salainen viesti");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate("chat?c=" + others.getId(), ChatView.class);

            assertTrue(hasTitle(window, "Valitse keskustelu"), "Keskustelua ei pidä avautua");
            assertFalse(window.find(MessageInput.class).single().isEnabled());
            assertEquals(0, messageCount(window));
        }
    }

    @Test
    void newGroup_withoutName_showsError() {
        AppUser me = TestData.newUser(users, "Perustaja");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(ChatView.class);

            window.findButton().withText("Uusi ryhmä").click();
            window.findButton().withText("Luo").click();

            assertEquals("Ryhmälle tarvitaan nimi", window.findNotification().getText());
            assertTrue(chats.conversationsOf(me).isEmpty());
        }
    }

    @Test
    void newGroup_withName_createsAndOpensGroup() {
        AppUser me = TestData.newUser(users, "Perustaja");
        String groupName = TestData.unique("Kalaporukka");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(ChatView.class);

            window.findButton().withText("Uusi ryhmä").click();
            window.findTextField().withLabel("Ryhmän nimi").setValue(groupName);
            window.findButton().withText("Luo").click();

            var groups = chats.conversationsOf(me);
            assertEquals(1, groups.size());
            assertTrue(groups.get(0).isGroupChat());
            assertEquals(groupName, groups.get(0).titleFor(me));
            assertTrue(hasTitle(window, groupName), "Uusi ryhmä pitäisi avautua");
            assertTrue(hasButton(window, groupName), "Ryhmä pitäisi näkyä listassa");
        }
    }
}
