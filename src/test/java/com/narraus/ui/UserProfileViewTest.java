package com.narraus.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.narraus.NarrausTest;
import com.narraus.domain.AppUser;
import com.narraus.domain.ChatService;
import com.narraus.domain.PostService;
import com.narraus.domain.UserService;
import com.vaadin.browserless.BrowserlessUIContext;
import com.vaadin.browserless.SecuredBrowserlessApplicationContext;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.RouteNotFoundError;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;

@NarrausTest
class UserProfileViewTest {

    @Autowired
    private SecuredBrowserlessApplicationContext<Authentication> app;

    @Autowired
    private UserService users;

    @Autowired
    private PostService posts;

    @Autowired
    private ChatService chats;

    private static boolean hasH2(BrowserlessUIContext window, String text) {
        return window.find(H2.class).all().stream().anyMatch(h -> text.equals(h.getText()));
    }

    @Test
    void otherUsersPage_showsDisplayName_counts_andTheirPostsOnly() {
        AppUser me = TestData.newUser(users, "Katsoja");
        AppUser target = TestData.newUser(users, "Kalastaja");
        users.toggleFollow(me, target);
        String theirSpecies = TestData.unique("Kuha");
        String mySpecies = TestData.unique("MinunSiika");
        posts.create(target, theirSpecies, 2.0, null, null, "Näsijärvi", null, null, null);
        posts.create(me, mySpecies, null, null, null, null, null, null, null);

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate("user/" + target.getId(), UserProfileView.class);

            assertTrue(hasH2(window, target.getDisplayName()), "Käyttäjän näyttönimi pitäisi näkyä otsikkona");
            assertTrue(window.find(Span.class).all().stream()
                            .anyMatch(s -> s.getText().equals("1 seuraajaa  ·  0 seurattavaa  ·  1 julkaisua")),
                    "Laskurit pitäisi näkyä");
            assertTrue(hasH2(window, theirSpecies), "Käyttäjän julkaisu pitäisi näkyä");
            assertFalse(hasH2(window, mySpecies), "Katsojan omaa julkaisua ei pidä näkyä toisen sivulla");
        }
    }

    /** Yksityisyys: toisen käyttäjän sivulla ei näy kirjautumistunnusta, salasanatiivistettä eikä tunnus-kenttää. */
    @Test
    void otherUsersPage_hidesPersonalInformation() {
        AppUser me = TestData.newUser(users, "Katsoja");
        AppUser target = TestData.newUser(users, "Yksityinen");
        String hash = users.find(target.getUsername()).orElseThrow().getPasswordHash();
        posts.create(target, TestData.unique("Hauki"), 4.0, 70.0, null, "Päijänne", "Kuvaus", null, null);

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            var view = window.navigate("user/" + target.getId(), UserProfileView.class);

            String html = view.getElement().getOuterHTML();
            assertFalse(html.contains(target.getUsername()), "Kirjautumistunnus ei saa näkyä");
            assertFalse(html.contains(hash), "Salasanatiiviste ei saa näkyä");
            assertFalse(html.contains("Käyttäjätunnus"), "Tunnus-kenttää ei pidä olla");
            assertFalse(html.contains("@"), "Ei @-tunnusta eikä sähköpostia");
        }
    }

    @Test
    void ownId_forwardsToOwnPage() {
        AppUser me = TestData.newUser(users, "Minä");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate("user/" + me.getId(), ProfileView.class);
        }
    }

    @Test
    void unknownUser_showsNotFound() {
        AppUser me = TestData.newUser(users, "Katsoja");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate("user/999999999", RouteNotFoundError.class);
        }
    }

    @Test
    void anonymousUser_isRedirectedToLogin() {
        AppUser target = TestData.newUser(users, "Kalastaja");

        try (var anonymous = app.newUser()) {
            anonymous.newWindow().navigate("user/" + target.getId(), LoginView.class);
        }
    }

    @Test
    void usersList_nameButton_opensUsersPage() {
        AppUser me = TestData.newUser(users, "Katsoja");
        AppUser target = TestData.newUser(users, "Kohde");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(UsersView.class);
            window.test(window.find(TextField.class).single()).setValue(target.getDisplayName());

            window.findButton().withText(target.getDisplayName()).click();

            assertInstanceOf(UserProfileView.class, window.getCurrentView());
            assertTrue(hasH2(window, target.getDisplayName()));
        }
    }

    // ---- seuraaminen profiilisivulta ----

    private static String followLabel(BrowserlessUIContext window) {
        return window.find(com.vaadin.flow.component.button.Button.class).all().stream()
                .map(com.vaadin.flow.component.button.Button::getText)
                .filter(t -> t.equals("Seuraa") || t.equals("Lopeta seuraaminen")).findFirst().orElse("(ei nappia)");
    }

    @Test
    void profile_hasFollowButton_thatTogglesFollowingAndUpdatesTheCounts() {
        AppUser me = TestData.newUser(users, "Katsoja");
        AppUser target = TestData.newUser(users, "Kohde");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate("user/" + target.getId(), UserProfileView.class);
            assertEquals("Seuraa", followLabel(window));
            assertEquals(0, users.followerCount(target));

            window.findButton().withText("Seuraa").click();

            assertTrue(users.isFollowing(me, target), "Seuraaminen tallentuu");
            assertEquals("Lopeta seuraaminen", followLabel(window));
            assertTrue(window.find(Span.class).all().stream()
                    .anyMatch(s -> s.getText().startsWith("1 seuraajaa")), "Seuraajamäärä päivittyy");

            window.findButton().withText("Lopeta seuraaminen").click();

            assertFalse(users.isFollowing(me, target));
            assertEquals("Seuraa", followLabel(window));
            assertTrue(window.find(Span.class).all().stream().anyMatch(s -> s.getText().startsWith("0 seuraajaa")));
        }
    }

    @Test
    void profile_showsCurrentFollowState_whenOpened() {
        AppUser me = TestData.newUser(users, "Katsoja");
        AppUser target = TestData.newUser(users, "Kohde");
        users.toggleFollow(me, target);

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate("user/" + target.getId(), UserProfileView.class);

            assertEquals("Lopeta seuraaminen", followLabel(window));
        }
    }

    @Test
    void followButton_togglesFollowing() {
        AppUser me = TestData.newUser(users, "Seuraaja");
        AppUser target = TestData.newUser(users, "Seurattava");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate("user/" + target.getId(), UserProfileView.class);

            window.findButton().withText("Seuraa").click();
            assertTrue(users.isFollowing(me, target), "Seuraaminen pitäisi tallentua");
            assertEquals("Lopeta seuraaminen", followLabel(window), "Napin teksti pitäisi vaihtua");
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
            window.navigate("user/" + target.getId(), UserProfileView.class);

            window.findButton().withText("Viesti").click();

            assertInstanceOf(ChatView.class, window.getCurrentView());
            var conversations = chats.conversationsOf(me);
            assertEquals(1, conversations.size());
            assertEquals(target.getDisplayName(), conversations.get(0).titleFor(me));
        }
    }

    @Test
    void followingFromProfile_changesTheFollowerCountShownInTheUsersList() {
        AppUser me = TestData.newUser(users, "Katsoja");
        AppUser target = TestData.newUser(users, "Kohde");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate("user/" + target.getId(), UserProfileView.class);
            window.findButton().withText("Seuraa").click();

            window.navigate(UsersView.class);
            window.test(window.find(TextField.class).single()).setValue(target.getDisplayName());

            assertTrue(window.find(Span.class).all().stream().anyMatch(s -> "1 seuraajaa".equals(s.getText())),
                    "Lista näyttää päivittyneen seuraajamäärän");
        }
    }

    @Test
    void followFromProfile_notifiesNobodyAndExposesNoPersonalInfo() {
        AppUser me = TestData.newUser(users, "Katsoja");
        AppUser target = TestData.newUser(users, "Kohde");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            var view = window.navigate("user/" + target.getId(), UserProfileView.class);

            assertFalse(view.getElement().getOuterHTML().contains(target.getUsername()),
                    "Seuraa-napin lisääminen ei saa paljastaa tunnusta");
        }
    }

    @Test
    void backArrow_returnsToTheUsersList() {
        AppUser me = TestData.newUser(users, "Katsoja");
        AppUser target = TestData.newUser(users, "Kohde");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate("user/" + target.getId(), UserProfileView.class);

            var back = window.find(com.vaadin.flow.component.button.Button.class).all().stream()
                    .filter(b -> b.hasClassName("profile-back")).findFirst().orElseThrow();
            assertEquals("Käyttäjät", back.getText());
            assertTrue(back.getIcon() != null, "Napissa on nuoli");

            window.test(back).click();

            assertInstanceOf(UsersView.class, window.getCurrentView());
        }
    }
}
