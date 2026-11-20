package com.narraus.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.narraus.NarrausTest;
import com.narraus.domain.AppUser;
import com.narraus.domain.ChatService;
import com.narraus.domain.Conversation;
import com.narraus.domain.PostService;
import com.narraus.domain.UserService;
import com.vaadin.browserless.BrowserlessUIContext;
import com.vaadin.browserless.SecuredBrowserlessApplicationContext;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.sidenav.SideNavItem;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;

/** Sivupaneelin lukemattomien merkit: näkyvät vain kun uutta on, 1-9 numerona ja sen jälkeen "9+". */
@NarrausTest
class NavBadgeTest {

    @Autowired
    private SecuredBrowserlessApplicationContext<Authentication> app;
    @Autowired
    private UserService users;
    @Autowired
    private PostService posts;
    @Autowired
    private ChatService chats;

    /** Kohdan merkin teksti, tai tyhjä jos merkkiä ei näytetä. */
    private static Optional<String> badge(BrowserlessUIContext window, String itemLabel) {
        SideNavItem item = window.find(SideNavItem.class).all().stream()
                .filter(i -> itemLabel.equals(i.getLabel())).findFirst().orElseThrow();
        Span badge = (Span) item.getSuffixComponent();
        return badge.isVisible() ? Optional.of(badge.getText()) : Optional.empty();
    }

    private void pause() throws InterruptedException {
        Thread.sleep(15);
    }

    private void messages(Conversation c, AppUser from, int count) {
        for (int i = 1; i <= count; i++) chats.send(c, from, "viesti " + i);
    }

    @Test
    void badgeText_showsNumbersUpToNine_thenNinePlus() {
        assertEquals("1", MainLayout.badgeText(1));
        assertEquals("2", MainLayout.badgeText(2));
        assertEquals("3", MainLayout.badgeText(3));
        assertEquals("9", MainLayout.badgeText(9));
        assertEquals("9+", MainLayout.badgeText(10));
        assertEquals("9+", MainLayout.badgeText(250));
    }

    @Test
    void noNewActivity_showsNoBadges() {
        AppUser me = TestData.newUser(users, "Minä");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(ProfileView.class);

            assertEquals(Optional.empty(), badge(window, "Saaliit"));
            assertEquals(Optional.empty(), badge(window, "Keskustelut"));
        }
    }

    @Test
    void unreadMessages_showCountOnChatItem() {
        AppUser me = TestData.newUser(users, "Minä");
        AppUser friend = TestData.newUser(users, "Kaveri");
        messages(chats.openDirect(me, friend), friend, 3);

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(ProfileView.class);

            assertEquals(Optional.of("3"), badge(window, "Keskustelut"));
            assertEquals(Optional.empty(), badge(window, "Saaliit"));
        }
    }

    @Test
    void tenOrMoreUnreadMessages_showNinePlus() {
        AppUser me = TestData.newUser(users, "Minä");
        AppUser friend = TestData.newUser(users, "Kaveri");
        messages(chats.openDirect(me, friend), friend, 12);

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(ProfileView.class);

            assertEquals(Optional.of("9+"), badge(window, "Keskustelut"));
        }
    }

    @Test
    void exactlyNineUnread_showsNine() {
        AppUser me = TestData.newUser(users, "Minä");
        AppUser friend = TestData.newUser(users, "Kaveri");
        messages(chats.openDirect(me, friend), friend, 9);

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(ProfileView.class);

            assertEquals(Optional.of("9"), badge(window, "Keskustelut"));
        }
    }

    @Test
    void readingTheConversation_removesTheBadge() {
        AppUser me = TestData.newUser(users, "Minä");
        AppUser friend = TestData.newUser(users, "Kaveri");
        messages(chats.openDirect(me, friend), friend, 2);

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(ChatView.class);
            assertEquals(Optional.of("2"), badge(window, "Keskustelut"));

            window.findButton().withText(friend.getDisplayName()).click();

            assertEquals(Optional.empty(), badge(window, "Keskustelut"), "Luettu keskustelu ei saa jäädä merkiksi");
        }
    }

    @Test
    void readingOneOfTwoConversations_leavesTheOthersCount() {
        AppUser me = TestData.newUser(users, "Minä");
        AppUser a = TestData.newUser(users, "A");
        AppUser b = TestData.newUser(users, "B");
        messages(chats.openDirect(me, a), a, 2);
        messages(chats.openDirect(me, b), b, 1);

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(ChatView.class);
            assertEquals(Optional.of("3"), badge(window, "Keskustelut"));

            window.findButton().withText(a.getDisplayName()).click();

            assertEquals(Optional.of("1"), badge(window, "Keskustelut"));
        }
    }

    @Test
    void newMessageInOpenConversation_isReadImmediately() {
        AppUser me = TestData.newUser(users, "Minä");
        AppUser friend = TestData.newUser(users, "Kaveri");
        Conversation c = chats.openDirect(me, friend);

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate("chat?c=" + c.getId(), ChatView.class);

            chats.send(c, friend, "tuore viesti");

            assertEquals(Optional.empty(), badge(window, "Keskustelut"), "Auki olevaan keskusteluun tullut viesti on luettu");
        }
    }

    @Test
    void newMessageInOtherView_showsBadgeLive() {
        AppUser me = TestData.newUser(users, "Minä");
        AppUser friend = TestData.newUser(users, "Kaveri");
        Conversation c = chats.openDirect(me, friend);

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(ProfileView.class);
            assertEquals(Optional.empty(), badge(window, "Keskustelut"));

            chats.send(c, friend, "hei");

            assertEquals(Optional.of("1"), badge(window, "Keskustelut"));
        }
    }

    @Test
    void newPostsFromOthers_showOnFeedItem_andOpeningFeedClearsThem() throws Exception {
        AppUser me = TestData.newUser(users, "Minä");
        AppUser other = TestData.newUser(users, "Toinen");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow(); // avaa etusivun eli syötteen: se on nyt nähty
            window.navigate(ProfileView.class); // siirrytään pois syötteestä
            pause();
            posts.create(other, "Hauki", null, null, null, null, null, null, null);
            posts.create(other, "Ahven", null, null, null, null, null, null, null);
            posts.create(me, "OmaKuha", null, null, null, null, null, null, null);

            assertEquals(Optional.of("2"), badge(window, "Saaliit"), "Omia julkaisuja ei lasketa");

            window.navigate(FeedView.class);

            assertEquals(Optional.empty(), badge(window, "Saaliit"));
        }
    }

    @Test
    void feedBadge_survivesPageReload_untilFeedIsOpened() throws Exception {
        AppUser me = TestData.newUser(users, "Minä");
        AppUser other = TestData.newUser(users, "Toinen");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var first = user.newWindow();
            first.navigate(ProfileView.class);
            pause();
            posts.create(other, "Hauki", null, null, null, null, null, null, null);
            assertEquals(Optional.of("1"), badge(first, "Saaliit"));

            // Toinen ikkuna samalta käyttäjältä lataa sovelluksen uudelleen ja jää muulle sivulle kuin syötteeseen
            var second = user.newWindow();
            second.navigate(ProfileView.class);
            assertEquals(Optional.empty(), badge(second, "Saaliit"), "Toisen ikkunan avaus näytti syötteen");
        }
    }

    @Test
    void newPostWhileBrowsing_showsBadgeLive_butNotWhileOnFeed() throws Exception {
        AppUser me = TestData.newUser(users, "Minä");
        AppUser other = TestData.newUser(users, "Toinen");
        pause();

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(ProfileView.class);
            assertEquals(Optional.empty(), badge(window, "Saaliit"));

            posts.create(other, "Hauki", null, null, null, null, null, null, null);
            assertEquals(Optional.of("1"), badge(window, "Saaliit"));

            window.navigate(FeedView.class);
            pause();
            posts.create(other, "Ahven", null, null, null, null, null, null, null);
            assertEquals(Optional.empty(), badge(window, "Saaliit"), "Syötteen ollessa auki uusi julkaisu on heti nähty");
        }
    }

    @Test
    void badgesAreForTheLoggedInUserOnly() {
        AppUser me = TestData.newUser(users, "Minä");
        AppUser a = TestData.newUser(users, "A");
        AppUser b = TestData.newUser(users, "B");
        messages(chats.openDirect(a, b), a, 5);

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(ProfileView.class);
            assertTrue(badge(window, "Keskustelut").isEmpty(), "Toisten keskustelut eivät näy minun merkissäni");
        }
    }
}
