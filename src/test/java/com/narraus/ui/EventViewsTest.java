package com.narraus.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.narraus.NarrausTest;
import com.narraus.domain.AppUser;
import com.narraus.domain.CatchPost;
import com.narraus.domain.CompetitionEvent;
import com.narraus.domain.EventMetric;
import com.narraus.domain.EventService;
import com.narraus.domain.PostService;
import com.narraus.domain.ReviewStatus;
import com.narraus.domain.UserService;
import com.vaadin.browserless.BrowserlessUIContext;
import com.vaadin.browserless.SecuredBrowserlessApplicationContext;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.messages.MessageInput;
import com.vaadin.flow.component.messages.MessageList;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;

/** Kilpailunäkymät: lista, kilpailusivu, tulostaulu, ylläpitäjän työkalut ja chat. */
@NarrausTest
class EventViewsTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3, 4};

    @Autowired
    private SecuredBrowserlessApplicationContext<Authentication> app;
    @Autowired
    private UserService users;
    @Autowired
    private EventService events;
    @Autowired
    private PostService posts;

    private AppUser admin() {
        return users.find("robbe").orElseGet(() -> users.register("robbe", "Robbe Ylläpitäjä", "salasana123"));
    }

    private CompetitionEvent ongoing(String name, EventMetric metric, int counted) {
        return events.create(admin(), name, "Kilpailun kuvaus", Instant.now().minus(Duration.ofHours(1)),
                Instant.now().plus(Duration.ofHours(1)), metric, null, counted);
    }

    private CatchPost fish(AppUser who, String species, Double weight, boolean photo, Duration ago) {
        return posts.create(who, species, weight, null, null, null, null, photo ? PNG : null, "image/png", null, null,
                Instant.now().minus(ago));
    }

    private static Stream<Component> all(Component c) {
        return Stream.concat(Stream.of(c), c.getChildren().flatMap(EventViewsTest::all));
    }

    private static Button button(BrowserlessUIContext w, String text) {
        return w.find(Button.class).all().stream().filter(b -> text.equals(b.getText())).findFirst().orElse(null);
    }

    private static boolean hasText(BrowserlessUIContext w, String text) {
        return w.find(Span.class).all().stream().anyMatch(s -> s.getText() != null && s.getText().contains(text))
                || w.find(com.vaadin.flow.component.html.Paragraph.class).all().stream()
                .anyMatch(p -> p.getText() != null && p.getText().contains(text));
    }

    private static String unique(String prefix) {
        return prefix + " " + UUID.randomUUID().toString().substring(0, 6);
    }

    // ---- navigaatio ja lista ----

    @Test
    void sideNav_hasTheEventsLink_forEveryone() {
        AppUser me = TestData.newUser(users, "Minä");
        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(FeedView.class);
            assertTrue(window.find(SideNavItem.class).all().stream().anyMatch(i -> "Kilpailut".equals(i.getLabel())));
        }
    }

    @Test
    void list_createButton_isOnlyForTheAdmin() {
        AppUser normal = TestData.newUser(users, "Tavallinen");
        try (var user = app.newUser(normal.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(EventsView.class);
            assertNotNull(window.find(Span.class), "Sivu aukeaa");
            assertEquals(null, button(window, "Uusi kilpailu"), "Tavallinen käyttäjä ei voi luoda kilpailuja");
        }
        try (var adminUser = app.newUser("robbe", "USER")) {
            admin();
            var window = adminUser.newWindow();
            window.navigate(EventsView.class);
            assertNotNull(button(window, "Uusi kilpailu"), "Ylläpitäjä näkee Uusi kilpailu -napin");
        }
    }

    @Test
    void list_groupsEventsByStatus_andShowsThem() {
        CompetitionEvent now = ongoing(unique("Käynnissä"), EventMetric.WEIGHT, 1);
        CompetitionEvent soon = events.create(admin(), unique("Tulossa"), null, Instant.now().plus(Duration.ofDays(1)),
                Instant.now().plus(Duration.ofDays(2)), EventMetric.LENGTH, "Hauki", 1);
        CompetitionEvent past = events.create(admin(), unique("Mennyt"), null, Instant.now().minus(Duration.ofDays(3)),
                Instant.now().minus(Duration.ofDays(2)), EventMetric.COUNT, null, 1);
        AppUser me = TestData.newUser(users, "Minä");
        events.join(now.getId(), me);

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            EventsView view = window.navigate(EventsView.class);
            String text = all(view).filter(c -> c instanceof Span || c instanceof com.vaadin.flow.component.html.H3)
                    .map(c -> c instanceof Span s ? s.getText() : ((com.vaadin.flow.component.html.H3) c).getText())
                    .reduce("", (a, b) -> a + "\n" + b);
            for (String name : new String[]{now.getName(), soon.getName(), past.getName()}) assertTrue(text.contains(name), name);
            assertTrue(text.contains("Käynnissä") && text.contains("Tulossa") && text.contains("Päättyneet"));
            assertTrue(text.contains("Olet mukana"), "Liittynyt näkee merkinnän");
            assertTrue(text.contains("Suurin paino") && text.contains("Pisin kala · Hauki") && text.contains("Eniten kaloja"));
        }
    }

    // ---- luonti ja muokkaus ----

    @Test
    void admin_createsAnEventWithTheDialog() {
        admin();
        String name = unique("Syyskisa");
        try (var adminUser = app.newUser("robbe", "USER")) {
            var window = adminUser.newWindow();
            window.navigate(EventsView.class);
            window.test(button(window, "Uusi kilpailu")).click();

            window.test(window.find(TextField.class).withLabel("Nimi").single()).setValue(name);
            window.find(Select.class).withLabel("Mitä mitataan").single().setValue(EventMetric.LENGTH);
            window.test(window.find(TextField.class).withLabel("Laji (tyhjä = kaikki lajit)").single()).setValue("Ahven");
            var start = window.find(DateTimePicker.class).withLabel("Alkaa").single();
            var end = window.find(DateTimePicker.class).withLabel("Päättyy").single();
            assertEquals(LocalDateTime.now().toLocalDate(), start.getValue().toLocalDate(), "Alkuehdotus on tämä päivä");
            assertTrue(end.getValue().isAfter(start.getValue()), "Päättyminen ehdotetaan alkamisen jälkeen");
            window.test(button(window, "Luo kilpailu")).click();

            var created = events.all().stream().filter(e -> name.equals(e.getName())).findFirst().orElseThrow();
            assertEquals(EventMetric.LENGTH, created.getMetric());
            assertEquals("Ahven", created.getSpecies());
            assertInstanceOf(EventView.class, window.getCurrentView(), "Luonnin jälkeen avautuu kilpailusivu");
        }
    }

    @Test
    void dialog_rejectsAnEndBeforeTheStart() {
        admin();
        String name = unique("Virheellinen");
        try (var adminUser = app.newUser("robbe", "USER")) {
            var window = adminUser.newWindow();
            window.navigate(EventsView.class);
            window.test(button(window, "Uusi kilpailu")).click();
            window.test(window.find(TextField.class).withLabel("Nimi").single()).setValue(name);
            var end = window.find(DateTimePicker.class).withLabel("Päättyy").single();
            window.test(end).setValue(LocalDateTime.now().minusDays(2).truncatedTo(ChronoUnit.MINUTES));
            window.test(button(window, "Luo kilpailu")).click();

            assertTrue(events.all().stream().noneMatch(e -> name.equals(e.getName())), "Tallennus estetään");
        }
    }

    @Test
    void eventPage_editAndDeleteButtons_areAdminOnly() {
        CompetitionEvent e = ongoing(unique("Kisa"), EventMetric.WEIGHT, 1);
        AppUser normal = TestData.newUser(users, "Tavallinen");

        try (var user = app.newUser(normal.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate("event/" + e.getId(), EventView.class);
            assertEquals(null, button(window, "Muokkaa kilpailua"));
            assertEquals(null, button(window, "Poista kilpailu"));
        }
        try (var adminUser = app.newUser("robbe", "USER")) {
            var window = adminUser.newWindow();
            window.navigate("event/" + e.getId(), EventView.class);
            assertNotNull(button(window, "Muokkaa kilpailua"));
            assertNotNull(button(window, "Poista kilpailu"));

            window.test(button(window, "Muokkaa kilpailua")).click();
            String newName = unique("Uusi nimi");
            window.test(window.find(TextField.class).withLabel("Nimi").single()).setValue(newName);
            window.test(button(window, "Tallenna")).click();
            assertEquals(newName, events.find(e.getId()).orElseThrow().getName());

            window.test(button(window, "Poista kilpailu")).click();
            window.test(window.find(ConfirmDialog.class).single()).confirm();
            assertTrue(events.find(e.getId()).isEmpty());
            assertInstanceOf(EventsView.class, window.getCurrentView());
        }
    }

    // ---- liittyminen ----

    @Test
    void anyoneCanJoinAndLeave_andTheAdminToo() {
        CompetitionEvent e = ongoing(unique("Kisa"), EventMetric.WEIGHT, 1);
        AppUser me = TestData.newUser(users, "Kilpailija");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate("event/" + e.getId(), EventView.class);
            assertEquals(null, button(window, "Lisää saalis"), "Saaliin lisäys vasta liittymisen jälkeen");

            window.test(button(window, "Liity kilpailuun")).click();
            assertTrue(events.isParticipant(e, me));
            assertNotNull(button(window, "Poistu kilpailusta"));
            assertNotNull(button(window, "Lisää saalis"), "Käynnissä olevaan kilpailuun voi lisätä saaliin");

            window.test(button(window, "Poistu kilpailusta")).click();
            assertFalse(events.isParticipant(e, me));
        }
        try (var adminUser = app.newUser("robbe", "USER")) {
            var window = adminUser.newWindow();
            window.navigate("event/" + e.getId(), EventView.class);
            window.test(button(window, "Liity kilpailuun")).click();
            assertTrue(events.isParticipant(e, admin()), "Ylläpitäjä saa kilpailla itse");
        }
    }

    @Test
    void endedEvent_hasNoJoinButton_andShowsFinalResultsWithAPodium() {
        AppUser a = TestData.newUser(users, "Ykkönen");
        AppUser b = TestData.newUser(users, "Kakkonen");
        AppUser c = TestData.newUser(users, "Kolmonen");
        AppUser d = TestData.newUser(users, "Neloinen");
        CompetitionEvent e = events.create(admin(), unique("Mennyt"), null, Instant.now().minus(Duration.ofDays(3)),
                Instant.now().plus(Duration.ofSeconds(2)), EventMetric.WEIGHT, null, 1);
        for (AppUser u : new AppUser[]{a, b, c, d}) events.join(e.getId(), u);
        fish(a, "Hauki", 9.0, true, Duration.ofDays(1));
        fish(b, "Hauki", 7.0, true, Duration.ofDays(1));
        fish(c, "Hauki", 5.0, true, Duration.ofDays(1));
        fish(d, "Hauki", 3.0, true, Duration.ofDays(1));
        events.update(e.getId(), admin(), e.getName(), null, e.getStartsAt(), Instant.now().minus(Duration.ofHours(1)),
                EventMetric.WEIGHT, null, 1);

        AppUser me = TestData.newUser(users, "Katsoja");
        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            EventView view = window.navigate("event/" + e.getId(), EventView.class);

            assertEquals(null, button(window, "Liity kilpailuun"), "Päättyneeseen ei voi liittyä");
            assertTrue(hasText(window, "Lopputulokset"));
            var podium = all(view).filter(x -> x instanceof Div dv && dv.hasClassName("podium")).toList();
            assertEquals(3, podium.size(), "Kolme parasta saa palkintokorokkeen");
            assertTrue(all(view).anyMatch(x -> x instanceof Div dv && dv.hasClassName("podium-1")));
            assertTrue(hasText(window, "🥇") && hasText(window, "🥈") && hasText(window, "🥉"));
            assertTrue(hasText(window, "9 kg") && hasText(window, "3 kg"), "Kaikki sijat näkyvät pisteineen");
        }
    }

    // ---- tulostaulu ----

    @Test
    void leaderboard_showsOnlyFishWithPhotos_andExplainsTheRule() {
        CompetitionEvent e = ongoing(unique("Kisa"), EventMetric.WEIGHT, 1);
        AppUser a = TestData.newUser(users, "Kuvallinen");
        AppUser b = TestData.newUser(users, "Kuvaton");
        events.join(e.getId(), a);
        events.join(e.getId(), b);
        fish(a, "Hauki", 5.4, true, Duration.ofMinutes(10));
        fish(b, "Hauki", 20.0, false, Duration.ofMinutes(10)); // ei kuvaa

        try (var user = app.newUser(a.getUsername(), "USER")) {
            var window = user.newWindow();
            EventView view = window.navigate("event/" + e.getId(), EventView.class);

            assertTrue(hasText(window, "Tilanne nyt"));
            assertTrue(hasText(window, "Vain kuvalliset saaliit lasketaan mukaan"));
            assertTrue(hasText(window, "5,4 kg"));
            assertTrue(hasText(window, a.getDisplayName()));
            assertFalse(hasText(window, b.getDisplayName()), "Kuvaton saalis ei tuo sijaa");
            assertFalse(hasText(window, "20 kg"));
            assertTrue(all(view).anyMatch(x -> x instanceof Div dv && dv.hasClassName("top-1")));
        }
    }

    @Test
    void admin_hasReviewTools_others_dont() {
        CompetitionEvent e = ongoing(unique("Kisa"), EventMetric.WEIGHT, 1);
        AppUser a = TestData.newUser(users, "Kilpailija");
        events.join(e.getId(), a);
        fish(a, "Hauki", 6.0, true, Duration.ofMinutes(10));

        try (var user = app.newUser(a.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate("event/" + e.getId(), EventView.class);
            assertEquals(null, button(window, "Kysy onko oikea"));
            assertEquals(null, button(window, "Poista kisasta"));
        }
        try (var adminUser = app.newUser("robbe", "USER")) {
            var window = adminUser.newWindow();
            window.navigate("event/" + e.getId(), EventView.class);
            assertNotNull(button(window, "Kysy onko oikea"));
            assertNotNull(button(window, "Poista kisasta"));
        }
    }

    @Test
    void admin_asksIfAFishIsCorrect_itStaysCountedButIsFlagged_andCanBeApproved() {
        CompetitionEvent e = ongoing(unique("Kisa"), EventMetric.WEIGHT, 1);
        AppUser a = TestData.newUser(users, "Kilpailija");
        events.join(e.getId(), a);
        fish(a, "Hauki", 6.0, true, Duration.ofMinutes(10));

        try (var adminUser = app.newUser("robbe", "USER")) {
            var window = adminUser.newWindow();
            window.navigate("event/" + e.getId(), EventView.class);

            window.test(button(window, "Kysy onko oikea")).click();
            window.test(window.find(TextArea.class).withLabel("Kysymys (näkyy kilpailun keskustelussa)").single())
                    .setValue("Onko paino punnittu?");
            window.test(button(window, "Kysy")).click();

            assertTrue(hasText(window, "⚠ Tarkistuksessa: Onko paino punnittu?"));
            assertTrue(hasText(window, "6 kg"), "Kysytty kala lasketaan yhä mukaan");
            assertEquals(null, button(window, "Kysy onko oikea"));
            assertNotNull(button(window, "Hyväksy"));
            assertTrue(events.messages(e.getId()).stream().anyMatch(m -> m.getText().contains("Onko paino punnittu?")),
                    "Kysymys menee kilpailun keskusteluun");

            window.test(button(window, "Hyväksy")).click();
            assertFalse(hasText(window, "⚠ Tarkistuksessa"));
        }
    }

    @Test
    void admin_removesAFishFromTheCompetition_andCanRestoreIt() {
        CompetitionEvent e = ongoing(unique("Kisa"), EventMetric.WEIGHT, 1);
        AppUser a = TestData.newUser(users, "Kilpailija");
        events.join(e.getId(), a);
        fish(a, "Hauki", 8.0, true, Duration.ofMinutes(10));

        try (var adminUser = app.newUser("robbe", "USER")) {
            var window = adminUser.newWindow();
            window.navigate("event/" + e.getId(), EventView.class);
            assertTrue(hasText(window, "8 kg"));

            window.test(button(window, "Poista kisasta")).click();
            window.test(window.find(TextArea.class).withLabel("Syy (näkyy kilpailun keskustelussa)").single())
                    .setValue("Kuva ei vastaa kalaa");
            window.test(button(window, "Poista")).click();

            assertEquals(1, events.leaderboard(e.getId()).rejected().size());
            assertEquals(0, events.leaderboard(e.getId()).standings().size());
            assertTrue(hasText(window, "Poistetut kalat (1)"));
            assertTrue(hasText(window, "Ei vielä tuloksia"));

            window.test(button(window, "Palauta")).click();
            assertEquals(0, events.leaderboard(e.getId()).rejected().size());
            assertEquals(1, events.leaderboard(e.getId()).standings().size());
            assertTrue(hasText(window, "8 kg"));
        }
    }

    @Test
    void nonAdmin_seesOnlyTheCountOfRemovedFish() {
        CompetitionEvent e = ongoing(unique("Kisa"), EventMetric.WEIGHT, 1);
        AppUser a = TestData.newUser(users, "Kilpailija");
        events.join(e.getId(), a);
        CatchPost p = fish(a, "Hauki", 8.0, true, Duration.ofMinutes(10));
        events.review(e.getId(), p.getId(), admin(), ReviewStatus.REJECTED, "Ei kelpaa");

        try (var user = app.newUser(a.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate("event/" + e.getId(), EventView.class);
            assertTrue(hasText(window, "1 kalaa on poistettu kilpailusta ylläpitäjän päätöksellä."));
            assertEquals(null, button(window, "Palauta"), "Palautus on vain ylläpitäjälle");
        }
    }

    // ---- chat ----

    @Test
    void chatTab_lets_anyoneWrite_evenWithoutJoining() {
        CompetitionEvent e = ongoing(unique("Kisa"), EventMetric.WEIGHT, 1);
        AppUser outsider = TestData.newUser(users, "Vierailija");

        try (var user = app.newUser(outsider.getUsername(), "USER")) {
            var window = user.newWindow();
            EventView view = window.navigate("event/" + e.getId(), EventView.class);
            all(view).filter(x -> x instanceof Tabs).map(x -> (Tabs) x).findFirst().orElseThrow().setSelectedIndex(1);

            assertNotNull(window.find(MessageInput.class).withClassName("event-chat-input").single());
            assertNotNull(button(window, "Kirjoita viesti…"), "Puhelimella kirjoitusnappi");
            window.test(window.find(MessageInput.class).single()).send("Moi kaikki");

            assertEquals(1, events.messages(e.getId()).size());
            assertEquals("Moi kaikki", events.messages(e.getId()).get(0).getText());
            assertEquals(1, window.test(window.find(MessageList.class).single()).size());
        }
    }

    @Test
    void chatCompose_opensATopAnchoredWriter_andSends() {
        CompetitionEvent e = ongoing(unique("Kisa"), EventMetric.WEIGHT, 1);
        AppUser me = TestData.newUser(users, "Kirjoittaja");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            EventView view = window.navigate("event/" + e.getId(), EventView.class);
            all(view).filter(x -> x instanceof Tabs).map(x -> (Tabs) x).findFirst().orElseThrow().setSelectedIndex(1);

            window.test(button(window, "Kirjoita viesti…")).click();
            var dialog = window.find(Dialog.class).all().stream().filter(Dialog::isOpened).findFirst().orElseThrow();
            assertEquals("0", dialog.getTop());
            window.test(window.find(TextArea.class).all().stream()
                    .filter(t -> "Kirjoita viesti…".equals(t.getPlaceholder())).findFirst().orElseThrow()).setValue("Puhelimesta");
            window.test(button(window, "Lähetä")).click();

            assertEquals("Puhelimesta", events.messages(e.getId()).get(0).getText());
        }
    }

    // ---- navigointi ----

    @Test
    void backArrow_isOnTheEventPage() {
        CompetitionEvent e = ongoing(unique("Kisa"), EventMetric.WEIGHT, 1);
        AppUser me = TestData.newUser(users, "Minä");
        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate("event/" + e.getId(), EventView.class);
            var back = window.find(Button.class).all().stream().filter(b -> b.hasClassName("back-button")).findFirst().orElseThrow();
            assertEquals("Takaisin", back.getElement().getAttribute("aria-label"));
            assertTrue(back.getIcon() != null, "Napissa on nuoli");
        }
    }

    @Test
    void unknownEvent_showsNotFound() {
        AppUser me = TestData.newUser(users, "Minä");
        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate("event/999999999", com.vaadin.flow.router.RouteNotFoundError.class);
        }
    }

    @Test
    void whenTheAdminDeletesTheEvent_otherViewersAreSentToTheList() {
        CompetitionEvent e = ongoing(unique("Poistuva"), EventMetric.WEIGHT, 1);
        AppUser me = TestData.newUser(users, "Katsoja");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate("event/" + e.getId(), EventView.class);

            events.delete(e.getId(), admin());

            // Ikkunakysely ajaa taustalta tulleet päivitykset (selaimeton ympäristö)
            window.find(Span.class).all();
            assertInstanceOf(EventsView.class, window.getCurrentView(), "Poistetun kilpailun katsoja ohjataan listaan");
        }
    }
}
