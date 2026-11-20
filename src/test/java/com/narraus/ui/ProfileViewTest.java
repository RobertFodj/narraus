package com.narraus.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.narraus.NarrausTest;
import com.narraus.domain.AppUser;
import com.narraus.domain.PostService;
import com.narraus.domain.UserService;
import com.vaadin.browserless.BrowserlessUIContext;
import com.vaadin.browserless.SecuredBrowserlessApplicationContext;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.sidenav.SideNavItem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;

@NarrausTest
class ProfileViewTest {

    @Autowired
    private SecuredBrowserlessApplicationContext<Authentication> app;

    @Autowired
    private UserService users;

    @Autowired
    private PostService posts;

    private static boolean hasSpan(BrowserlessUIContext window, String text) {
        return window.find(Span.class).all().stream().anyMatch(s -> text.equals(s.getText()));
    }

    private static boolean hasSpeciesHeading(BrowserlessUIContext window, String species) {
        return window.find(H2.class).all().stream().anyMatch(h -> species.equals(h.getText()));
    }

    @Test
    void ownPage_showsLoginInfoAndDetails() {
        AppUser me = TestData.newUser(users, "Minä");
        AppUser other = TestData.newUser(users, "Toinen");
        users.toggleFollow(me, other);     // minä seuraan toista
        users.toggleFollow(other, me);     // ja toinen seuraa minua
        posts.create(me, TestData.unique("Hauki"), 3.5, null, null, null, null, null, null);

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(ProfileView.class);
            TestData.openSection(window, "Omat julkaisut");

            assertTrue(hasSpan(window, "Käyttäjätunnus: " + me.getUsername()), "Kirjautumistunnus pitäisi näkyä omalla sivulla");
            assertTrue(hasSpan(window, "Näyttönimi: " + me.getDisplayName()));
            assertTrue(hasSpan(window, "Seuraajia: 1"));
            assertTrue(hasSpan(window, "Seurattavia: 1"));
            assertTrue(hasSpan(window, "Julkaisuja: 1"));
        }
    }

    @Test
    void ownPage_neverShowsPasswordOrItsHash() {
        AppUser me = TestData.newUser(users, "Minä");
        String hash = users.find(me.getUsername()).orElseThrow().getPasswordHash();

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            var view = window.navigate(ProfileView.class);
            TestData.openSection(window, "Omat julkaisut");

            String html = view.getElement().getOuterHTML();
            assertFalse(html.contains(hash), "Salasanatiivistettä ei saa näyttää");
            assertFalse(html.contains("salasana123"), "Salasanaa ei saa näyttää");
        }
    }

    @Test
    void ownPage_showsOnlyOwnPosts() {
        AppUser me = TestData.newUser(users, "Minä");
        AppUser other = TestData.newUser(users, "Toinen");
        String mine = TestData.unique("OmaHauki");
        String theirs = TestData.unique("ToisenAhven");
        posts.create(me, mine, null, null, null, null, null, null, null);
        posts.create(other, theirs, null, null, null, null, null, null, null);

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(ProfileView.class);
            TestData.openSection(window, "Omat julkaisut");

            assertTrue(hasSpeciesHeading(window, mine), "Oma julkaisu pitäisi näkyä");
            assertFalse(hasSpeciesHeading(window, theirs), "Toisen julkaisua ei pidä näkyä omalla sivulla");
        }
    }

    @Test
    void ownPage_withoutPosts_showsHint() {
        AppUser me = TestData.newUser(users, "Minä");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(ProfileView.class);
            TestData.openSection(window, "Omat julkaisut");

            assertTrue(window.find(com.vaadin.flow.component.html.Paragraph.class).all().stream()
                    .anyMatch(p -> p.getText().startsWith("Et ole vielä julkaissut")));
            assertTrue(hasSpan(window, "Julkaisuja: 0"));
        }
    }

    @Test
    void sideNav_hasLinkToOwnPage() {
        AppUser me = TestData.newUser(users, "Minä");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(FeedView.class);

            assertTrue(window.find(SideNavItem.class).all().stream().anyMatch(i -> "Oma tili".equals(i.getLabel())),
                    "Sivunavigaatiossa pitäisi olla Oma tili");
        }
    }

    @Test
    void anonymousUser_isRedirectedToLogin() {
        try (var anonymous = app.newUser()) {
            anonymous.newWindow().navigate("profile", LoginView.class);
        }
    }
}
