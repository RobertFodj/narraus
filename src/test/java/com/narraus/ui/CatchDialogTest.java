package com.narraus.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;

/** Julkaisun sijainti: piste kartalta tai paikan nimellä (kartta kohdistuu paikkaan). */
@NarrausTest
class CatchDialogTest {

    @Autowired
    private SecuredBrowserlessApplicationContext<Authentication> app;
    @Autowired
    private UserService users;
    @Autowired
    private PostService posts;

    private CatchDialog openDialog(BrowserlessUIContext window) {
        window.navigate(FeedView.class);
        window.findButton().withText("Uusi saalis").click();
        return window.find(CatchDialog.class).single();
    }

    private CatchPost onlyPostOf(AppUser me) {
        var mine = posts.byAuthor(me);
        assertEquals(1, mine.size(), "Julkaisun pitäisi tallentua");
        return mine.get(0);
    }

    @Test
    void placeName_searchPlacesPinAndFocusesMap() {
        AppUser me = TestData.newUser(users, "Minä");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            CatchDialog dialog = openDialog(window);

            window.findTextField().withLabel("Paikka").setValue("Päijänne");
            window.findButton().withText("Hae kartalta").click();

            assertEquals(61.5, dialog.pinLatitude());
            assertEquals(25.5, dialog.pinLongitude());
        }
    }

    @Test
    void searchedPlace_isSavedWithTheCatch() {
        AppUser me = TestData.newUser(users, "Minä");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            openDialog(window);

            TestData.species(window).setValue("Hauki");
            window.findTextField().withLabel("Paikka").setValue("Päijänne");
            window.findButton().withText("Hae kartalta").click();
            window.findButton().withText("Julkaise").click();

            CatchPost post = onlyPostOf(me);
            assertEquals(61.5, post.getLatitude());
            assertEquals(25.5, post.getLongitude());
            assertEquals("Päijänne", post.getLocation());
        }
    }

    @Test
    void clickOnMap_setsPin_andIsSaved() {
        AppUser me = TestData.newUser(users, "Minä");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            CatchDialog dialog = openDialog(window);

            dialog.setPin(62.24, 25.75); // sama polku kuin kartan klikkaus
            TestData.species(window).setValue("Kuha");
            window.findButton().withText("Julkaise").click();

            CatchPost post = onlyPostOf(me);
            assertEquals(62.24, post.getLatitude());
            assertEquals(25.75, post.getLongitude());
        }
    }

    @Test
    void secondClick_movesThePin_insteadOfAddingAnother() {
        AppUser me = TestData.newUser(users, "Minä");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            CatchDialog dialog = openDialog(window);

            dialog.setPin(60.0, 24.0);
            dialog.setPin(63.0, 27.0);

            assertEquals(63.0, dialog.pinLatitude());
            assertEquals(27.0, dialog.pinLongitude());
        }
    }

    @Test
    void removePin_clearsTheLocation() {
        AppUser me = TestData.newUser(users, "Minä");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            CatchDialog dialog = openDialog(window);
            dialog.setPin(62.0, 26.0);

            window.findButton().withText("Poista piste").click();

            assertNull(dialog.pinLatitude());
            assertNull(dialog.pinLongitude());
            TestData.species(window).setValue("Ahven");
            window.findButton().withText("Julkaise").click();
            CatchPost post = onlyPostOf(me);
            assertNull(post.getLatitude());
            assertNull(post.getLongitude());
        }
    }

    @Test
    void unknownPlace_showsNotification_andSetsNoPin() {
        AppUser me = TestData.newUser(users, "Minä");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            CatchDialog dialog = openDialog(window);

            window.findTextField().withLabel("Paikka").setValue("Ei-olemassa-paikka");
            window.findButton().withText("Hae kartalta").click();

            assertEquals("Paikkaa ei löytynyt. Valitse piste kartalta.", window.findNotification().getText());
            assertNull(dialog.pinLatitude());
        }
    }

    @Test
    void searchWithEmptyPlace_asksForAName() {
        AppUser me = TestData.newUser(users, "Minä");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            openDialog(window);

            window.findButton().withText("Hae kartalta").click();

            assertEquals("Kirjoita ensin paikan nimi", window.findNotification().getText());
        }
    }

    @Test
    void typedPlaceWithoutSearching_isLocatedOnSave() {
        AppUser me = TestData.newUser(users, "Minä");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            openDialog(window);

            TestData.species(window).setValue("Hauki");
            window.findTextField().withLabel("Paikka").setValue("Lahti");
            window.findButton().withText("Julkaise").click();

            CatchPost post = onlyPostOf(me);
            assertNotNull(post.getLatitude(), "Kirjoitettu paikka pitäisi paikantua tallennettaessa");
            assertEquals(60.98, post.getLatitude());
            assertEquals(25.66, post.getLongitude());
        }
    }

    @Test
    void noPlace_isSavedWithoutCoordinates() {
        AppUser me = TestData.newUser(users, "Minä");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            openDialog(window);

            TestData.species(window).setValue("Särki");
            window.findButton().withText("Julkaise").click();

            CatchPost post = onlyPostOf(me);
            assertNull(post.getLatitude());
            assertNull(post.getLongitude());
            assertTrue(post.getLocation() == null);
        }
    }

    @Test
    void unlocatableTypedPlace_isStillSaved_withoutCoordinates() {
        AppUser me = TestData.newUser(users, "Minä");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            openDialog(window);

            TestData.species(window).setValue("Lahna");
            window.findTextField().withLabel("Paikka").setValue("Salainen lampi");
            window.findButton().withText("Julkaise").click();

            CatchPost post = onlyPostOf(me);
            assertEquals("Salainen lampi", post.getLocation());
            assertNull(post.getLatitude());
        }
    }

    // ---- saaliin aika ----

    private static com.vaadin.flow.component.datetimepicker.DateTimePicker timePicker(BrowserlessUIContext window) {
        return window.find(com.vaadin.flow.component.datetimepicker.DateTimePicker.class).withLabel("Saaliin aika").single();
    }

    @Test
    void newCatch_suggestsTodayAndNow_andSavesTheSuggestion() {
        AppUser me = TestData.newUser(users, "Minä");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            openDialog(window);

            var suggested = timePicker(window).getValue();
            assertEquals(java.time.LocalDate.now(), suggested.toLocalDate(), "Ehdotus on aina tämän päivän päivä");
            assertTrue(java.time.Duration.between(suggested, java.time.LocalDateTime.now()).abs().toMinutes() <= 2,
                    "Ja nykyinen kellonaika");

            TestData.species(window).setValue("Hauki");
            window.findButton().withText("Julkaise").click();

            var saved = java.time.LocalDateTime.ofInstant(onlyPostOf(me).getCaughtAt(), java.time.ZoneId.systemDefault());
            assertEquals(suggested, saved.truncatedTo(java.time.temporal.ChronoUnit.MINUTES));
        }
    }

    @Test
    void newCatch_timeCanBeChanged() {
        AppUser me = TestData.newUser(users, "Minä");
        var wanted = java.time.LocalDateTime.now().minusDays(2).withHour(6).withMinute(30).withSecond(0).withNano(0);

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            openDialog(window);
            TestData.species(window).setValue("Ahven");
            window.test(timePicker(window)).setValue(wanted);
            window.findButton().withText("Julkaise").click();

            assertEquals(wanted, java.time.LocalDateTime.ofInstant(onlyPostOf(me).getCaughtAt(), java.time.ZoneId.systemDefault()));
        }
    }

    @Test
    void newCatch_emptyTime_isNotSaved() {
        AppUser me = TestData.newUser(users, "Minä");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            openDialog(window);
            TestData.species(window).setValue("Kuha");
            window.test(timePicker(window)).setValue(null);
            window.findButton().withText("Julkaise").click();

            assertEquals(0, posts.byAuthor(me).size(), "Ilman aikaa julkaisua ei tallenneta");
        }
    }

    @Test
    void editing_showsTheSavedTime_andCanChangeIt() {
        AppUser me = TestData.newUser(users, "Minä");
        var oldTime = java.time.LocalDateTime.now().minusDays(1).withMinute(15).withSecond(0).withNano(0);
        posts.create(me, "Hauki", null, null, null, null, null, null, null, null, null,
                oldTime.atZone(java.time.ZoneId.systemDefault()).toInstant());
        var newTime = oldTime.minusDays(3).withHour(5);

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(ProfileView.class);
            TestData.openSection(window, "Omat julkaisut");
            window.findButton().withText("Muokkaa").click();

            assertEquals(oldTime, timePicker(window).getValue(), "Muokkaus näyttää tallennetun ajan");
            assertTrue(timePicker(window).getHelperText().contains("muuttaa"));

            window.test(timePicker(window)).setValue(newTime);
            window.findButton().withText("Tallenna").click();

            assertEquals(newTime, java.time.LocalDateTime.ofInstant(onlyPostOf(me).getCaughtAt(), java.time.ZoneId.systemDefault()));
        }
    }

    @Test
    void speciesIsAComboBox_withTheSixteenFish_inOrder_andNoDescriptions() {
        AppUser me = TestData.newUser(users, "Minä");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(FeedView.class);
            window.findButton().withText("Uusi saalis").click();

            var species = TestData.species(window);
            var items = species.getListDataView().getItems().toList();
            assertEquals(java.util.List.of("Ahven", "Hauki", "Kuha", "Made", "Särki", "Lahna", "Salakka", "Pasuri",
                    "Sorva", "Säyne", "Kirjolohi", "Siika", "Muikku", "Taimen", "Lohi", "Harjus"), items);
            assertTrue(items.stream().allMatch(i -> !i.contains(" ") && !i.contains(".")), "Vain lajin nimi, ei selitystä");
            assertTrue(species.isAllowCustomValue(), "Muun lajin voi kirjoittaa");
        }
    }
}
