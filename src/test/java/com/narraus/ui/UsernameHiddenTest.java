package com.narraus.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.narraus.NarrausTest;
import com.narraus.domain.AppUser;
import com.narraus.domain.CatchPost;
import com.narraus.domain.ChatService;
import com.narraus.domain.Conversation;
import com.narraus.domain.PostService;
import com.narraus.domain.UserService;
import com.vaadin.browserless.BrowserlessUIContext;
import com.vaadin.browserless.SecuredBrowserlessApplicationContext;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.messages.MessageList;
import com.vaadin.flow.component.textfield.TextField;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;

/** Toisten käyttäjien kirjautumistunnukset eivät saa näkyä missään. Oma tunnus näkyy vain omalla sivulla. */
@NarrausTest
class UsernameHiddenTest {

    @Autowired
    private SecuredBrowserlessApplicationContext<Authentication> app;
    @Autowired
    private UserService users;
    @Autowired
    private PostService posts;
    @Autowired
    private ChatService chats;

    private static boolean hasText(BrowserlessUIContext window, String text) {
        return window.find(Span.class).all().stream().anyMatch(s -> s.getText().contains(text))
                || window.find(com.vaadin.flow.component.button.Button.class).all().stream()
                        .anyMatch(b -> text.equals(b.getText()));
    }

    @Test
    void feed_showsDisplayNamesOfOthers_notUsernames() {
        AppUser me = TestData.newUser(users, "Katsoja");
        AppUser other = TestData.newUser(users, "Kirjoittaja");
        posts.create(other, TestData.unique("Hauki"), null, null, null, "Päijänne", null, null, null);

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            FeedView view = window.navigate(FeedView.class);

            String html = view.getElement().getOuterHTML();
            assertTrue(html.contains(other.getDisplayName()), "Näyttönimi näkyy kortissa");
            assertFalse(html.contains(other.getUsername()), "Tunnus ei saa näkyä syötteessä");
        }
    }

    @Test
    void peopleList_showsDisplayNameOnly() {
        AppUser me = TestData.newUser(users, "Katsoja");
        AppUser other = TestData.newUser(users, "Kohde");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            UsersView view = window.navigate(UsersView.class);
            window.test(window.find(TextField.class).single()).setValue(other.getDisplayName());

            String html = view.getElement().getOuterHTML();
            assertTrue(hasText(window, other.getDisplayName()));
            assertFalse(html.contains(other.getUsername()), "Tunnus ei saa näkyä Käyttäjät-listassa");
            assertFalse(html.contains("(@"), "Ei @-tunnusta");
        }
    }

    @Test
    void people_cannotBeFoundByTheirUsername() {
        AppUser me = TestData.newUser(users, "Katsoja");
        AppUser other = TestData.newUser(users, "Kohde");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(UsersView.class);

            window.test(window.find(TextField.class).single()).setValue(other.getUsername());

            assertTrue(hasText(window, "Käyttäjiä ei löytynyt."), "Tunnuksella haku ei saa paljastaa käyttäjää");
        }
        assertTrue(users.search(other.getUsername()).isEmpty());
        assertEquals(1, users.search(other.getDisplayName()).size(), "Näyttönimellä haku toimii edelleen");
    }

    @Test
    void chat_showsDisplayNamesOnly() {
        AppUser me = TestData.newUser(users, "Minä");
        AppUser friend = TestData.newUser(users, "Kaveri");
        Conversation c = chats.openDirect(me, friend);
        chats.send(c, friend, "hei");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            ChatView view = window.navigate("chat?c=" + c.getId(), ChatView.class);

            assertFalse(view.getElement().getOuterHTML().contains(friend.getUsername()));
            var shown = window.test(window.find(MessageList.class).single()).getMessages();
            assertEquals(friend.getDisplayName(), shown.get(0).getUserName(), "Viestin lähettäjänä näyttönimi");
        }
    }

    @Test
    void groupDialog_listsDisplayNamesOnly() {
        AppUser me = TestData.newUser(users, "Minä");
        AppUser other = TestData.newUser(users, "Toinen");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(ChatView.class);
            window.findButton().withText("Uusi ryhmä").click();

            @SuppressWarnings("unchecked")
            MultiSelectComboBox<AppUser> members = window.find(MultiSelectComboBox.class).single();
            assertEquals(other.getDisplayName(), members.getItemLabelGenerator().apply(other),
                    "Valinnassa näkyy näyttönimi");
            assertFalse(members.getItemLabelGenerator().apply(other).contains(other.getUsername()),
                    "Jäsenvalinnassa ei tunnuksia");
        }
        // Haku, josta valinta ammentaa: löytyy näyttönimellä, ei tunnuksella, eikä sisällä hakijaa itseään
        assertEquals(1, users.searchOthers(other.getDisplayName(), me, 0, 50).size());
        assertTrue(users.searchOthers(other.getUsername(), me, 0, 50).isEmpty(), "Tunnuksella ei voi hakea");
        assertTrue(users.searchOthers(me.getDisplayName(), me, 0, 50).isEmpty(), "Itseä ei tarjota jäseneksi");
    }

    @Test
    void mapDetails_showNoUsername() {
        AppUser me = TestData.newUser(users, "Katsoja");
        AppUser other = TestData.newUser(users, "Kalastaja");
        CatchPost post = posts.create(other, TestData.unique("Kuha"), null, null, null, "Lahti", null, null, null,
                60.98, 25.66);

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            MapView view = window.navigate(MapView.class);
            view.openDetails(post);

            String html = window.find(CatchCard.class).all().stream()
                    .map(c -> c.getElement().getOuterHTML()).reduce("", String::concat);
            assertTrue(html.contains(other.getDisplayName()));
            assertFalse(html.contains(other.getUsername()));
        }
    }

    @Test
    void ownUsername_isShownOnlyOnOwnPage() {
        AppUser me = TestData.newUser(users, "Minä");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            ProfileView own = window.navigate(ProfileView.class);
            assertTrue(own.getElement().getOuterHTML().contains(me.getUsername()), "Oma tunnus näkyy omalla sivulla");

            FeedView feed = window.navigate(FeedView.class);
            assertFalse(feed.getElement().getOuterHTML().contains(me.getUsername()), "Ei muualla");
        }
    }

    @Test
    void register_requiresDisplayName_soUsernameNeverBecomesTheVisibleName() {
        assertThrows(IllegalArgumentException.class, () -> users.register(TestData.unique("u"), "", "salasana123"));
        assertThrows(IllegalArgumentException.class, () -> users.register(TestData.unique("u"), "   ", "salasana123"));
        assertThrows(IllegalArgumentException.class, () -> users.register(TestData.unique("u"), null, "salasana123"));

        String username = TestData.unique("u");
        AppUser created = users.register(username, "  Kalle Kalastaja  ", "salasana123");
        assertEquals("Kalle Kalastaja", created.getDisplayName());
        assertFalse(created.getDisplayName().contains(username));
        assertEquals(Set.of(), Set.copyOf(users.search(username)), "Tunnuksella ei löydy");
    }
}
