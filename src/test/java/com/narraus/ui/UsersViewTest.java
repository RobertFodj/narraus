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
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.component.textfield.TextField;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;

@NarrausTest
class UsersViewTest {

    @Autowired
    private SecuredBrowserlessApplicationContext<Authentication> app;

    @Autowired
    private UserService users;

    @Autowired
    private ChatService chats;

    @Autowired
    private PostService posts;

    private void search(BrowserlessUIContext window, String query) {
        TextField field = window.find(TextField.class).single();
        window.test(field).setValue(query);
    }

    private boolean hasText(BrowserlessUIContext window, String text) {
        return window.find(Span.class).all().stream().anyMatch(s -> s.getText().contains(text))
                || window.find(Button.class).all().stream().anyMatch(b -> text.equals(b.getText()));
    }

    @Test
    void search_showsMatchingUser_butNeverTheLoggedInUser() {
        AppUser me = TestData.newUser(users, "Etsija");
        AppUser target = TestData.newUser(users, "Kohde");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(UsersView.class);

            search(window, target.getDisplayName());
            assertTrue(hasText(window, target.getDisplayName()), "Haettu käyttäjä pitäisi näkyä tuloksissa");

            search(window, me.getDisplayName());
            assertTrue(hasText(window, "Käyttäjiä ei löytynyt."), "Omaa itseä ei listata");
        }
    }

    @Test
    void search_withNoMatches_showsEmptyMessage() {
        AppUser me = TestData.newUser(users, "Etsija");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(UsersView.class);

            search(window, TestData.unique("ei-olemassa"));
            assertTrue(hasText(window, "Käyttäjiä ei löytynyt."));
        }
    }

    // ---- Käyttäjät (aiemmin Ihmiset) ----

    @Test
    void page_isCalledUsers_notPeople() {
        AppUser me = TestData.newUser(users, "Minä");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(UsersView.class);

            assertTrue(window.find(H2.class).all().stream().anyMatch(h -> "Käyttäjät".equals(h.getText())));
            assertTrue(window.find(SideNavItem.class).all().stream().anyMatch(i -> "Käyttäjät".equals(i.getLabel())));
            assertFalse(window.find(SideNavItem.class).all().stream().anyMatch(i -> "Ihmiset".equals(i.getLabel())),
                    "Vanhaa nimeä Ihmiset ei saa jäädä navigaatioon");
            assertFalse(window.find(H2.class).all().stream().anyMatch(h -> "Ihmiset".equals(h.getText())));
        }
    }

    @Test
    void oldPeopleAddress_stillOpensTheUsersPage() {
        AppUser me = TestData.newUser(users, "Minä");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate("people", UsersView.class);
            window.navigate("users", UsersView.class);
        }
    }

    @Test
    void nameIsAButton_thatOpensTheUsersProfile_withTheSameInfoAsTheProfilePage() {
        AppUser me = TestData.newUser(users, "Katsoja");
        AppUser target = TestData.newUser(users, "Kohde");
        String species = TestData.unique("Kuha");
        posts.create(target, species, null, null, null, null, null, null, null);

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(UsersView.class);
            search(window, target.getDisplayName());

            window.findButton().withText(target.getDisplayName()).click();

            assertInstanceOf(UserProfileView.class, window.getCurrentView());
            assertTrue(window.find(H2.class).all().stream().anyMatch(h -> target.getDisplayName().equals(h.getText())));
            assertTrue(window.find(H2.class).all().stream().anyMatch(h -> species.equals(h.getText())),
                    "Profiilissa näkyvät käyttäjän julkaisut");
            assertTrue(hasText(window, "0 seuraajaa  ·  0 seurattavaa  ·  1 julkaisua"));
        }
    }

    @Test
    void separateProfileButton_isGone() {
        AppUser me = TestData.newUser(users, "Katsoja");
        AppUser target = TestData.newUser(users, "Kohde");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(UsersView.class);
            search(window, target.getDisplayName());

            assertEquals(0, window.find(Button.class).all().stream().filter(b -> "Profiili".equals(b.getText())).count(),
                    "Nimi toimii profiilinappina, erillistä Profiili-nappia ei enää ole");
        }
    }

    @Test
    void list_showsOnlyAFollowButton_andNoneOnceFollowing() {
        AppUser me = TestData.newUser(users, "Katsoja");
        AppUser target = TestData.newUser(users, "Kohde");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(UsersView.class);
            search(window, target.getDisplayName());

            assertEquals(1, window.find(Button.class).all().stream().filter(b -> "Seuraa".equals(b.getText())).count());
            assertTrue(window.find(Button.class).all().stream()
                            .noneMatch(b -> List.of("Lopeta seuraaminen", "Viesti").contains(b.getText())),
                    "Listalla ei ole viesti- eikä lopeta-nappia");

            window.findButton().withText("Seuraa").click();

            assertTrue(users.isFollowing(me, target));
            assertTrue(window.find(Span.class).all().stream().anyMatch(s -> "1 seuraajaa".equals(s.getText())));
            assertTrue(window.find(Button.class).all().stream().noneMatch(b -> "Seuraa".equals(b.getText())),
                    "Seuraamisen jälkeen nappia ei ole");
        }
    }

    @Test
    void list_hasNoFollowButton_forSomeoneAlreadyFollowed() {
        AppUser me = TestData.newUser(users, "Katsoja");
        AppUser target = TestData.newUser(users, "Kohde");
        users.toggleFollow(me, target);

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(UsersView.class);
            search(window, target.getDisplayName());

            assertTrue(window.find(Button.class).all().stream().noneMatch(b -> "Seuraa".equals(b.getText())));
        }
    }
}
