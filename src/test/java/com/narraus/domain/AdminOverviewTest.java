package com.narraus.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.narraus.NarrausTest;
import com.narraus.domain.AdminService.UserOverview;
import com.vaadin.flow.server.webpush.WebPushKeys;
import com.vaadin.flow.server.webpush.WebPushSubscription;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Ylläpitäjän näkymä käyttäjiin: tunnukset, kilpailut ja app/web-käyttö. Vain ylläpitäjälle. */
@NarrausTest
class AdminOverviewTest {

    @Autowired
    private AdminService adminService;
    @Autowired
    private UserService users;
    @Autowired
    private EventService events;
    @Autowired
    private PostService posts;
    @Autowired
    private PushService push;
    @Autowired
    private JdbcTemplate jdbc;

    private AppUser admin() {
        return users.find("robbe").orElseGet(() -> users.register("robbe", "Robbe Ylläpitäjä", "salasana123"));
    }

    private AppUser newUser(String prefix) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        return users.register("u-" + id, prefix + "-" + id, "salasana123");
    }

    private UserOverview overviewOf(AppUser user) {
        return adminService.overview(admin()).stream().filter(o -> o.user().equals(user)).findFirst().orElseThrow();
    }

    private CompetitionEvent newEvent(String name) {
        return events.create(admin(), name, "Kuvaus", Instant.now().minus(Duration.ofHours(1)),
                Instant.now().plus(Duration.ofHours(1)), EventMetric.WEIGHT, null, 3);
    }

    // ---- pääsy ----

    @Test
    void onlyTheAdmin_mayLookAtTheOverview() {
        AppUser stranger = newUser("Tavallinen");
        AppUser target = newUser("Kohde");

        assertThrows(SecurityException.class, () -> adminService.overview(stranger));
        assertThrows(SecurityException.class, () -> adminService.overviewOf(stranger, target.getId()));
        assertThrows(SecurityException.class, () -> adminService.overview(null));
        assertThrows(SecurityException.class, () -> adminService.overviewOf(null, target.getId()));
        assertFalse(adminService.overview(admin()).isEmpty());
    }

    @Test
    void overviewOf_unknownUser_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> adminService.overviewOf(admin(), 987654321L));
    }

    // ---- tunnukset ----

    @Test
    void admin_seesEveryUsersUsername() {
        AppUser a = newUser("Aino");

        UserOverview o = overviewOf(a);

        assertEquals(a.getUsername(), o.username(), "Ylläpitäjä näkee tunnuksen, jonka muut eivät näe");
        assertEquals(a.getDisplayName(), o.user().getDisplayName());
        assertEquals(a.getUsername(), adminService.overviewOf(admin(), a.getId()).username());
    }

    @Test
    void overview_isSortedByDisplayName_ignoringCase() {
        String tag = UUID.randomUUID().toString().substring(0, 6);
        AppUser b = users.register("b-" + tag, "bertta-" + tag, "salasana123");
        AppUser a = users.register("a-" + tag, "Aarne-" + tag, "salasana123");
        AppUser c = users.register("c-" + tag, "Cecilia-" + tag, "salasana123");

        List<String> names = adminService.overview(admin()).stream().map(o -> o.user().getDisplayName())
                .filter(n -> n.endsWith(tag)).toList();

        assertEquals(List.of(a.getDisplayName(), b.getDisplayName(), c.getDisplayName()), names);
    }

    // ---- kilpailut ----

    @Test
    void overview_listsTheCompetitionsTheUserTakesPartIn() {
        AppUser racer = newUser("Kisaaja");
        AppUser bystander = newUser("Katsoja");
        String tag = UUID.randomUUID().toString().substring(0, 6);
        CompetitionEvent first = newEvent("Kesäkisa-" + tag);
        CompetitionEvent second = newEvent("Syyskisa-" + tag);
        events.join(first.getId(), racer);
        events.join(second.getId(), racer);

        UserOverview racerInfo = overviewOf(racer);
        UserOverview bystanderInfo = overviewOf(bystander);

        assertEquals(List.of("Kesäkisa-" + tag, "Syyskisa-" + tag), racerInfo.events().stream()
                .map(CompetitionEvent::getName).sorted().toList());
        assertTrue(bystanderInfo.events().isEmpty(), "Kilpailuun osallistumaton ei saa listautua siihen");
    }

    @Test
    void leavingACompetition_removesItFromTheOverview() {
        AppUser racer = newUser("Kisaaja");
        CompetitionEvent event = newEvent("Poistuva-" + UUID.randomUUID().toString().substring(0, 6));
        events.join(event.getId(), racer);
        assertEquals(1, overviewOf(racer).events().size());

        events.leave(event.getId(), racer);

        assertTrue(overviewOf(racer).events().isEmpty());
    }

    // ---- app vai web ----

    @Test
    void clientLabel_saysWhetherTheUserUsesTheAppTheWebOrBoth() {
        AppUser none = newUser("Ei-tietoa");
        AppUser appOnly = newUser("Appi");
        AppUser webOnly = newUser("Webbi");
        AppUser both = newUser("Molemmat");
        users.recordClient(appOnly.getId(), true);
        users.recordClient(webOnly.getId(), false);
        users.recordClient(both.getId(), true);
        users.recordClient(both.getId(), false);

        assertEquals("Ei tietoa", overviewOf(none).clientLabel());
        assertEquals("App", overviewOf(appOnly).clientLabel());
        assertEquals("Web", overviewOf(webOnly).clientLabel());
        assertEquals("App ja web", overviewOf(both).clientLabel());
        assertTrue(overviewOf(appOnly).usesApp());
        assertFalse(overviewOf(appOnly).usesWeb());
        assertNotNull(overviewOf(both).appLastUsedAt());
        assertNotNull(overviewOf(both).webLastUsedAt());
    }

    @Test
    void recordClient_isThrottled_butUpdatesOnceTheOldValueIsStale() {
        AppUser me = newUser("Käyttäjä");
        users.recordClient(me.getId(), true);
        Instant first = overviewOf(me).appLastUsedAt();

        users.recordClient(me.getId(), true); // heti uudelleen: ei kirjata
        assertEquals(first, overviewOf(me).appLastUsedAt(), "Kymmenen minuutin sisällä ei päivitetä");

        jdbc.update("update app_user set app_last_used_at = ? where id = ?",
                Timestamp.from(Instant.now().minus(Duration.ofMinutes(30))), me.getId());
        users.recordClient(me.getId(), true);
        assertTrue(overviewOf(me).appLastUsedAt().isAfter(Instant.now().minus(Duration.ofMinutes(1))),
                "Vanhentunut arvo päivittyy");
    }

    @Test
    void recordClient_forAnApp_doesNotTouchTheWebTimestamp_andViceVersa() {
        AppUser me = newUser("Käyttäjä");
        users.recordClient(me.getId(), true);

        assertNotNull(overviewOf(me).appLastUsedAt());
        assertNull(overviewOf(me).webLastUsedAt());
    }

    @Test
    void recordClient_withUnknownOrMissingUser_doesNothing() {
        users.recordClient(null, true);
        users.recordClient(987654321L, false);
    }

    // ---- muu ----

    @Test
    void overview_countsPushDevicesAndPosts() {
        AppUser me = newUser("Käyttäjä");
        push.subscribe(me, new WebPushSubscription("https://push.example.test/" + UUID.randomUUID(),
                new WebPushKeys("k", "a")));
        push.subscribe(me, new WebPushSubscription("https://push.example.test/" + UUID.randomUUID(),
                new WebPushKeys("k", "a")));
        posts.create(me, "Hauki", null, null, null, null, null, null, null);

        UserOverview o = overviewOf(me);

        assertEquals(2, o.pushDevices());
        assertEquals(1, o.posts());
    }
}
