package com.narraus.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.narraus.NarrausTest;
import com.narraus.domain.AppUser;
import com.narraus.domain.ChatMessage;
import com.narraus.domain.ChatService;
import com.narraus.domain.Conversation;
import com.narraus.domain.UserService;
import com.vaadin.browserless.BrowserlessUIContext;
import com.vaadin.browserless.SecuredBrowserlessApplicationContext;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.messages.MessageInput;
import com.vaadin.flow.component.messages.MessageList;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextArea;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;

/** Viestien ja ryhmien muokkaus keskustelunäkymässä. */
@NarrausTest
class ChatEditViewTest {

    @Autowired
    private SecuredBrowserlessApplicationContext<Authentication> app;
    @Autowired
    private UserService users;
    @Autowired
    private ChatService chats;

    private static boolean hasTitle(BrowserlessUIContext window, String text) {
        return window.find(H3.class).all().stream().anyMatch(h -> text.equals(h.getText()));
    }

    private static boolean visibleButton(BrowserlessUIContext window, String text) {
        return window.find(Button.class).all().stream().anyMatch(b -> text.equals(b.getText()) && b.isVisible());
    }

    private static ChatMessage lastMessage(ChatService chats, Conversation c, AppUser viewer) {
        var all = chats.messages(c, viewer);
        return all.get(all.size() - 1);
    }

    // ---- viestin muokkaus ----

    @Test
    void editMessagesButton_isHiddenUntilAConversationIsOpen() {
        AppUser me = TestData.newUser(users, "Minä");
        AppUser friend = TestData.newUser(users, "Kaveri");
        chats.openDirect(me, friend);

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(ChatView.class);
            assertFalse(visibleButton(window, "Muokkaa viestejä"));
            assertFalse(visibleButton(window, "Muokkaa ryhmää"));

            window.findButton().withText(friend.getDisplayName()).click();

            assertTrue(visibleButton(window, "Muokkaa viestejä"));
            assertFalse(visibleButton(window, "Muokkaa ryhmää"), "Suoraan keskusteluun ei ole ryhmän muokkausta");
        }
    }

    @Test
    void editMessage_changesTextAndShowsEditedMarker() {
        AppUser me = TestData.newUser(users, "Minä");
        AppUser friend = TestData.newUser(users, "Kaveri");
        Conversation c = chats.openDirect(me, friend);
        chats.send(c, me, "Alkuperäinen teksti");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate("chat?c=" + c.getId(), ChatView.class);
            window.findButton().withText("Muokkaa viestejä").click();

            TextArea text = window.find(TextArea.class).withLabel("Viestin teksti").single();
            assertEquals("Alkuperäinen teksti", text.getValue(), "Dialogi esitäyttää uusimman oman viestin");
            window.test(text).setValue("Korjattu teksti");
            window.findButton().withText("Tallenna").click();

            assertEquals("Korjattu teksti", lastMessage(chats, c, me).getText());
            assertTrue(lastMessage(chats, c, me).isEdited());
            var shown = window.test(window.find(MessageList.class).single()).getMessages();
            assertEquals(1, shown.size());
            assertEquals("Korjattu teksti", shown.get(0).getText());
            assertTrue(shown.get(0).getUserName().endsWith("(muokattu)"), "Lista merkitsee viestin muokatuksi");
        }
    }

    @Test
    void editMessage_isVisibleLiveToTheOtherParticipant() {
        AppUser me = TestData.newUser(users, "Minä");
        AppUser friend = TestData.newUser(users, "Kaveri");
        Conversation c = chats.openDirect(me, friend);
        chats.send(c, me, "Väärin kirjoitettu");

        try (var mine = app.newUser(me.getUsername(), "USER"); var theirs = app.newUser(friend.getUsername(), "USER")) {
            var myWindow = mine.newWindow();
            var friendWindow = theirs.newWindow();
            myWindow.navigate("chat?c=" + c.getId(), ChatView.class);
            friendWindow.navigate("chat?c=" + c.getId(), ChatView.class);

            myWindow.findButton().withText("Muokkaa viestejä").click();
            myWindow.test(myWindow.find(TextArea.class).withLabel("Viestin teksti").single()).setValue("Oikein");
            myWindow.findButton().withText("Tallenna").click();

            var seen = friendWindow.test(friendWindow.find(MessageList.class).single()).getMessages();
            assertEquals("Oikein", seen.get(0).getText(), "Toinen osapuoli näkee muokkauksen heti");
        }
    }

    @Test
    void editMessage_emptyText_isRejectedWithMessage() {
        AppUser me = TestData.newUser(users, "Minä");
        AppUser friend = TestData.newUser(users, "Kaveri");
        Conversation c = chats.openDirect(me, friend);
        chats.send(c, me, "Säilyy");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate("chat?c=" + c.getId(), ChatView.class);
            window.findButton().withText("Muokkaa viestejä").click();

            window.test(window.find(TextArea.class).withLabel("Viestin teksti").single()).setValue("   ");
            window.findButton().withText("Tallenna").click();

            assertEquals("Viesti ei voi olla tyhjä", window.findNotification().getText());
            assertEquals("Säilyy", lastMessage(chats, c, me).getText());
        }
    }

    @Test
    void editMessages_listsOnlyMyOwnMessages() {
        AppUser me = TestData.newUser(users, "Minä");
        AppUser friend = TestData.newUser(users, "Kaveri");
        Conversation c = chats.openDirect(me, friend);
        chats.send(c, me, "oma yksi");
        chats.send(c, friend, "kaverin viesti");
        chats.send(c, me, "oma kaksi");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate("chat?c=" + c.getId(), ChatView.class);
            window.findButton().withText("Muokkaa viestejä").click();

            @SuppressWarnings("unchecked")
            Select<ChatMessage> pick = window.find(Select.class).single();
            assertEquals(2, pick.getListDataView().getItemCount(), "Vain omat viestit valittavissa");
            assertTrue(pick.getListDataView().getItems().allMatch(m -> m.getSender().equals(me)));
        }
    }

    @Test
    void editMessages_withoutOwnMessages_showsHint() {
        AppUser me = TestData.newUser(users, "Minä");
        AppUser friend = TestData.newUser(users, "Kaveri");
        Conversation c = chats.openDirect(me, friend);
        chats.send(c, friend, "vain kaverin viesti");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate("chat?c=" + c.getId(), ChatView.class);
            window.findButton().withText("Muokkaa viestejä").click();

            assertTrue(window.find(Span.class).all().stream()
                    .anyMatch(s -> "Et ole vielä lähettänyt viestejä tässä keskustelussa.".equals(s.getText())));
        }
    }

    // ---- ryhmän muokkaus ----

    @Test
    void editGroup_button_isShownForGroups_andDialogIsPrefilled() {
        AppUser me = TestData.newUser(users, "Minä");
        AppUser a = TestData.newUser(users, "A");
        String name = TestData.unique("Porukka");
        Conversation g = chats.createGroup(me, name, Set.of(a));

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate("chat?c=" + g.getId(), ChatView.class);
            assertTrue(visibleButton(window, "Muokkaa ryhmää"));

            window.findButton().withText("Muokkaa ryhmää").click();

            assertEquals(name, window.findTextField().withLabel("Ryhmän nimi").component().getValue());
            @SuppressWarnings("unchecked")
            MultiSelectComboBox<AppUser> members = window.find(MultiSelectComboBox.class).single();
            assertEquals(Set.of(a), members.getValue(), "Nykyiset jäsenet (ei itseä) esivalittuina");
        }
    }

    @Test
    void editGroup_rename_updatesTitleAndStoredName() {
        AppUser me = TestData.newUser(users, "Minä");
        AppUser a = TestData.newUser(users, "A");
        Conversation g = chats.createGroup(me, TestData.unique("Vanha"), Set.of(a));
        String newName = TestData.unique("Uusi");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate("chat?c=" + g.getId(), ChatView.class);
            window.findButton().withText("Muokkaa ryhmää").click();

            window.findTextField().withLabel("Ryhmän nimi").setValue(newName);
            window.findButton().withText("Tallenna").click();

            assertEquals(newName, chats.get(g.getId(), me).getName());
            assertTrue(hasTitle(window, newName), "Otsikko päivittyy");
            assertTrue(window.find(Button.class).all().stream().anyMatch(b -> newName.equals(b.getText())),
                    "Ryhmälista päivittyy");
        }
    }

    @Test
    void editGroup_emptyName_isRejected() {
        AppUser me = TestData.newUser(users, "Minä");
        AppUser a = TestData.newUser(users, "A");
        String name = TestData.unique("Ryhmä");
        Conversation g = chats.createGroup(me, name, Set.of(a));

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate("chat?c=" + g.getId(), ChatView.class);
            window.findButton().withText("Muokkaa ryhmää").click();

            window.findTextField().withLabel("Ryhmän nimi").setValue("");
            window.findButton().withText("Tallenna").click();

            assertEquals("Ryhmälle tarvitaan nimi", window.findNotification().getText());
            assertEquals(name, chats.get(g.getId(), me).getName());
        }
    }

    @Test
    void editGroup_addMember_givesThemAccess() {
        AppUser me = TestData.newUser(users, "Minä");
        AppUser a = TestData.newUser(users, "A");
        AppUser newcomer = TestData.newUser(users, "Tulokas");
        Conversation g = chats.createGroup(me, TestData.unique("Ryhmä"), Set.of(a));
        assertNull(chats.get(g.getId(), newcomer));

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate("chat?c=" + g.getId(), ChatView.class);
            window.findButton().withText("Muokkaa ryhmää").click();

            @SuppressWarnings("unchecked")
            MultiSelectComboBox<AppUser> members = window.find(MultiSelectComboBox.class).single();
            members.setValue(Set.of(a, newcomer));
            window.findButton().withText("Tallenna").click();

            assertEquals(Set.of(me, a, newcomer), chats.get(g.getId(), me).getParticipants());
            assertTrue(chats.conversationsOf(newcomer).stream().anyMatch(c -> c.getId().equals(g.getId())));
        }
    }

    @Test
    void editGroup_otherMemberSeesTheNewNameLive() {
        AppUser me = TestData.newUser(users, "Minä");
        AppUser a = TestData.newUser(users, "A");
        Conversation g = chats.createGroup(me, TestData.unique("Vanha"), Set.of(a));
        String newName = TestData.unique("Uusi");

        try (var mine = app.newUser(me.getUsername(), "USER"); var theirs = app.newUser(a.getUsername(), "USER")) {
            var myWindow = mine.newWindow();
            var aWindow = theirs.newWindow();
            myWindow.navigate("chat?c=" + g.getId(), ChatView.class);
            aWindow.navigate("chat?c=" + g.getId(), ChatView.class);

            myWindow.findButton().withText("Muokkaa ryhmää").click();
            myWindow.findTextField().withLabel("Ryhmän nimi").setValue(newName);
            myWindow.findButton().withText("Tallenna").click();

            assertTrue(hasTitle(aWindow, newName), "Toisen jäsenen otsikko päivittyy heti");
        }
    }

    @Test
    void editGroup_removedMember_isKickedOutOfTheOpenConversation() {
        AppUser me = TestData.newUser(users, "Minä");
        AppUser removed = TestData.newUser(users, "Poistettava");
        Conversation g = chats.createGroup(me, TestData.unique("Ryhmä"), Set.of(removed));
        chats.send(g, me, "salainen viesti");

        try (var mine = app.newUser(me.getUsername(), "USER"); var theirs = app.newUser(removed.getUsername(), "USER")) {
            var myWindow = mine.newWindow();
            var removedWindow = theirs.newWindow();
            myWindow.navigate("chat?c=" + g.getId(), ChatView.class);
            removedWindow.navigate("chat?c=" + g.getId(), ChatView.class);
            assertEquals(1, removedWindow.test(removedWindow.find(MessageList.class).single()).size());

            myWindow.findButton().withText("Muokkaa ryhmää").click();
            @SuppressWarnings("unchecked")
            MultiSelectComboBox<AppUser> members = myWindow.find(MultiSelectComboBox.class).single();
            members.setValue(Set.of());
            myWindow.findButton().withText("Tallenna").click();

            assertTrue(hasTitle(removedWindow, "Valitse keskustelu"), "Poistetulta suljetaan keskustelu");
            assertFalse(removedWindow.find(MessageInput.class).single().isEnabled());
            assertEquals(0, removedWindow.test(removedWindow.find(MessageList.class).single()).size(),
                    "Poistettu jäsen ei saa jäädä lukemaan viestejä");
            assertNull(chats.get(g.getId(), removed));
        }
    }
}
