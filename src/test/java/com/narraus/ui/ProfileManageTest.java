package com.narraus.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.narraus.NarrausTest;
import com.narraus.domain.AppUser;
import com.narraus.domain.CatchPost;
import com.narraus.domain.PostService;
import com.narraus.domain.UserService;
import com.vaadin.browserless.BrowserlessUIContext;
import com.vaadin.browserless.SecuredBrowserlessApplicationContext;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;

/** Oman sivun julkaisujen muokkaus ja poisto sekä kartan tyyli. */
@NarrausTest
class ProfileManageTest {

    @Autowired
    private SecuredBrowserlessApplicationContext<Authentication> app;
    @Autowired
    private UserService users;
    @Autowired
    private PostService posts;
    @Autowired
    private Environment env;

    private static long buttons(BrowserlessUIContext window, String text) {
        return window.find(Button.class).all().stream().filter(b -> text.equals(b.getText())).count();
    }

    private static boolean hasH2(BrowserlessUIContext window, String text) {
        return window.find(H2.class).all().stream().anyMatch(h -> text.equals(h.getText()));
    }

    private static boolean hasSpan(BrowserlessUIContext window, String text) {
        return window.find(Span.class).all().stream().anyMatch(s -> text.equals(s.getText()));
    }

    private CatchPost only(AppUser me) {
        var mine = posts.byAuthor(me);
        assertEquals(1, mine.size());
        return mine.get(0);
    }

    // ---- näkyvyys ----

    @Test
    void ownPage_showsEditAndDeleteForEveryOwnPost() {
        AppUser me = TestData.newUser(users, "Minä");
        posts.create(me, TestData.unique("Hauki"), null, null, null, null, null, null, null);
        posts.create(me, TestData.unique("Ahven"), null, null, null, null, null, null, null);

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(ProfileView.class);
            TestData.openSection(window, "Omat julkaisut");

            assertEquals(2, buttons(window, "Muokkaa"));
            assertEquals(2, buttons(window, "Poista"));
        }
    }

    @Test
    void otherUsersPage_hasNoEditOrDeleteButtons() {
        AppUser me = TestData.newUser(users, "Katsoja");
        AppUser other = TestData.newUser(users, "Toinen");
        posts.create(other, TestData.unique("Hauki"), null, null, null, null, null, null, null);

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate("user/" + other.getId(), UserProfileView.class);

            assertEquals(0, buttons(window, "Muokkaa"), "Toisen julkaisua ei saa voida muokata");
            assertEquals(0, buttons(window, "Poista"), "Toisen julkaisua ei saa voida poistaa");
        }
    }

    // ---- muokkaus ----

    @Test
    void edit_opensPrefilledDialog_withPinAtTheSavedLocation() {
        AppUser me = TestData.newUser(users, "Minä");
        String species = TestData.unique("Hauki");
        posts.create(me, species, 2.5, 70.0, null, "Päijänne", "Hieno kala", null, null, 61.5, 25.5);

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(ProfileView.class);
            TestData.openSection(window, "Omat julkaisut");

            window.findButton().withText("Muokkaa").click();

            CatchDialog dialog = window.find(CatchDialog.class).single();
            assertEquals(species, TestData.species(window).getValue());
            assertEquals("Päijänne", window.findTextField().withLabel("Paikka").component().getValue());
            assertEquals(61.5, dialog.pinLatitude());
            assertEquals(25.5, dialog.pinLongitude());
            assertTrue(buttons(window, "Tallenna") == 1, "Tallennusnapin teksti on Tallenna, ei Julkaise");
            assertEquals(0, buttons(window, "Julkaise"));
        }
    }

    @Test
    void edit_save_updatesThePost_andThePage() {
        AppUser me = TestData.newUser(users, "Minä");
        posts.create(me, TestData.unique("Hauki"), 2.5, null, null, "Päijänne", null, null, null, 61.5, 25.5);
        String newSpecies = TestData.unique("Kuha");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(ProfileView.class);
            TestData.openSection(window, "Omat julkaisut");
            window.findButton().withText("Muokkaa").click();

            TestData.species(window).setValue(newSpecies);
            window.findButton().withText("Tallenna").click();

            assertEquals(newSpecies, only(me).getSpecies());
            assertTrue(hasH2(window, newSpecies), "Sivun pitäisi päivittyä uuteen lajiin");
            assertEquals(0, window.find(CatchDialog.class).all().stream().filter(d -> d.isOpened()).count(),
                    "Dialogi sulkeutuu tallennuksen jälkeen");
        }
    }

    @Test
    void edit_emptySpecies_isNotSaved() {
        AppUser me = TestData.newUser(users, "Minä");
        String species = TestData.unique("Hauki");
        posts.create(me, species, null, null, null, null, null, null, null);

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(ProfileView.class);
            TestData.openSection(window, "Omat julkaisut");
            window.findButton().withText("Muokkaa").click();

            TestData.species(window).setValue(null);
            window.findButton().withText("Tallenna").click();

            assertEquals(species, only(me).getSpecies(), "Tyhjää lajia ei tallenneta");
        }
    }

    @Test
    void edit_searchNewPlace_movesThePin() {
        AppUser me = TestData.newUser(users, "Minä");
        posts.create(me, TestData.unique("Hauki"), null, null, null, "Lahti", null, null, null, 60.98, 25.66);

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(ProfileView.class);
            TestData.openSection(window, "Omat julkaisut");
            window.findButton().withText("Muokkaa").click();

            window.findTextField().withLabel("Paikka").setValue("Päijänne");
            window.findButton().withText("Hae kartalta").click();
            window.findButton().withText("Tallenna").click();

            CatchPost post = only(me);
            assertEquals(61.5, post.getLatitude());
            assertEquals(25.5, post.getLongitude());
            assertEquals("Päijänne", post.getLocation());
        }
    }

    @Test
    void edit_removePin_clearsCoordinates_evenIfPlaceNameRemains() {
        AppUser me = TestData.newUser(users, "Minä");
        posts.create(me, TestData.unique("Hauki"), null, null, null, "Päijänne", null, null, null, 61.5, 25.5);

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(ProfileView.class);
            TestData.openSection(window, "Omat julkaisut");
            window.findButton().withText("Muokkaa").click();

            window.findButton().withText("Poista piste").click();
            window.findButton().withText("Tallenna").click();

            CatchPost post = only(me);
            assertNull(post.getLatitude(), "Tahallisesti poistettua pistettä ei paikanneta uudelleen nimellä");
            assertNull(post.getLongitude());
            assertEquals("Päijänne", post.getLocation());
        }
    }

    @Test
    void edit_postWithoutLocation_canGetOneByMapClick() {
        AppUser me = TestData.newUser(users, "Minä");
        posts.create(me, TestData.unique("Hauki"), null, null, null, null, null, null, null);

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(ProfileView.class);
            TestData.openSection(window, "Omat julkaisut");
            window.findButton().withText("Muokkaa").click();
            CatchDialog dialog = window.find(CatchDialog.class).single();
            assertNull(dialog.pinLatitude());

            dialog.setPin(63.1, 27.2);
            window.findButton().withText("Tallenna").click();

            CatchPost post = only(me);
            assertEquals(63.1, post.getLatitude());
            assertEquals(27.2, post.getLongitude());
        }
    }

    @Test
    void edit_changesShowOnTheMap() {
        AppUser me = TestData.newUser(users, "Minä");
        CatchPost post = posts.create(me, TestData.unique("Hauki"), null, null, null, null, null, null, null,
                61.5, 25.5);

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(ProfileView.class);
            TestData.openSection(window, "Omat julkaisut");
            window.findButton().withText("Muokkaa").click();
            window.find(CatchDialog.class).single().setPin(65.0, 26.0);
            window.findButton().withText("Tallenna").click();

            MapView map = window.navigate(MapView.class);
            var shown = map.shownPosts().stream().filter(p -> p.getId().equals(post.getId())).findFirst();
            assertTrue(shown.isPresent());
            assertEquals(65.0, shown.get().getLatitude());
        }
    }

    // ---- poisto ----

    @Test
    void delete_asksForConfirmation_andRemovesThePostWhenConfirmed() {
        AppUser me = TestData.newUser(users, "Minä");
        String species = TestData.unique("Hauki");
        posts.create(me, species, null, null, null, null, null, null, null);

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(ProfileView.class);
            TestData.openSection(window, "Omat julkaisut");
            assertTrue(hasSpan(window, "Julkaisuja: 1"));

            window.findButton().withText("Poista").click();
            ConfirmDialog confirm = window.find(ConfirmDialog.class).single();
            assertTrue(confirm.isOpened(), "Poisto vaatii vahvistuksen");
            assertEquals(1, posts.postCount(me), "Ennen vahvistusta mitään ei ole poistettu");

            window.test(confirm).confirm();

            assertEquals(0, posts.postCount(me));
            assertFalse(hasH2(window, species), "Poistettu julkaisu katoaa sivulta");
            assertTrue(hasSpan(window, "Julkaisuja: 0"));
            assertTrue(window.find(com.vaadin.flow.component.html.Paragraph.class).all().stream()
                    .anyMatch(p -> p.getText().startsWith("Et ole vielä julkaissut")));
        }
    }

    @Test
    void delete_cancel_keepsThePost() {
        AppUser me = TestData.newUser(users, "Minä");
        String species = TestData.unique("Hauki");
        posts.create(me, species, null, null, null, null, null, null, null);

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(ProfileView.class);
            TestData.openSection(window, "Omat julkaisut");

            window.findButton().withText("Poista").click();
            window.test(window.find(ConfirmDialog.class).single()).cancel();

            assertEquals(1, posts.postCount(me));
            assertTrue(hasH2(window, species));
        }
    }

    @Test
    void delete_oneOfTwo_leavesTheOther() {
        AppUser me = TestData.newUser(users, "Minä");
        posts.create(me, TestData.unique("Vanha"), null, null, null, null, null, null, null);
        String keep = TestData.unique("Uusi");
        posts.create(me, keep, null, null, null, null, null, null, null); // uusin ensin -> ensimmäinen kortti

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(ProfileView.class);
            TestData.openSection(window, "Omat julkaisut");

            window.find(Button.class).all().stream().filter(b -> "Poista".equals(b.getText())).skip(1).findFirst()
                    .ifPresent(b -> window.test(b).click()); // toinen kortti = vanhempi julkaisu
            window.test(window.find(ConfirmDialog.class).single()).confirm();

            assertEquals(1, posts.postCount(me));
            assertEquals(keep, posts.byAuthor(me).get(0).getSpecies());
        }
    }

    @Test
    void deletedPost_disappearsFromMapAndFeed() {
        AppUser me = TestData.newUser(users, "Minä");
        CatchPost post = posts.create(me, TestData.unique("Hauki"), null, null, null, null, null, null, null,
                61.5, 25.5);

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(ProfileView.class);
            TestData.openSection(window, "Omat julkaisut");
            window.findButton().withText("Poista").click();
            window.test(window.find(ConfirmDialog.class).single()).confirm();

            MapView map = window.navigate(MapView.class);
            assertFalse(map.shownPosts().stream().anyMatch(p -> p.getId().equals(post.getId())));
            assertFalse(posts.feed(me, false).stream().anyMatch(p -> p.getId().equals(post.getId())));
        }
    }

    // ---- kartan tyyli ----

    @Test
    void mapStyle_isOutdoorV4() {
        assertEquals("outdoor-v4", env.getProperty("narraus.map-style"));
        assertEquals("https://api.maptiler.com/maps/outdoor-v4/style.json?key=abc",
                new MapConfig("abc", "outdoor-v4").styleUrl());
        assertNotNull(new MapConfig("", "outdoor-v4").styleUrl(), "Ilman avainta käytetään varakarttaa");
        assertFalse(new MapConfig("", "outdoor-v4").styleUrl().contains("maptiler"));
    }
}
