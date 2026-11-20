package com.narraus.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.narraus.NarrausTest;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@NarrausTest
class EventServiceTest {

    private static final String PASSWORD = "salasana123";
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3, 4};

    @Autowired
    private EventService events;
    @Autowired
    private UserService users;
    @Autowired
    private PostService posts;
    @Autowired
    private AccountService accounts;

    private AppUser admin() {
        return users.find("robbe").orElseGet(() -> users.register("robbe", "Robbe Ylläpitäjä", PASSWORD));
    }

    private AppUser newUser(String prefix) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        return users.register("u-" + id, prefix + "-" + id, PASSWORD);
    }

    private static Instant ago(Duration d) {
        return Instant.now().minus(d).truncatedTo(ChronoUnit.SECONDS);
    }

    private static Instant ahead(Duration d) {
        return Instant.now().plus(d).truncatedTo(ChronoUnit.SECONDS);
    }

    /** Käynnissä oleva painokilpailu (alkoi tunti sitten, päättyy tunnin päästä). */
    private CompetitionEvent ongoing(EventMetric metric, String species, int counted) {
        return events.create(admin(), "Kisa " + UUID.randomUUID().toString().substring(0, 6), "Kuvaus",
                ago(Duration.ofHours(1)), ahead(Duration.ofHours(1)), metric, species, counted);
    }

    private CatchPost fish(AppUser who, String species, Double weight, Double length, boolean photo, Instant caught) {
        return posts.create(who, species, weight, length, null, null, null, photo ? PNG : null, "image/png", null,
                null, caught);
    }

    // ---- oikeudet ----

    @Test
    void onlyTheAdmin_canCreateEditAndDeleteEvents() {
        AppUser normal = newUser("Tavallinen");
        assertThrows(SecurityException.class, () -> events.create(normal, "Kisa", null, ago(Duration.ofHours(1)),
                ahead(Duration.ofHours(1)), EventMetric.WEIGHT, null, 1));

        CompetitionEvent e = ongoing(EventMetric.WEIGHT, null, 1);
        assertThrows(SecurityException.class, () -> events.update(e.getId(), normal, "Uusi", null, e.getStartsAt(),
                e.getEndsAt(), EventMetric.LENGTH, null, 1));
        assertThrows(SecurityException.class, () -> events.delete(e.getId(), normal));

        events.update(e.getId(), admin(), "Uusi nimi", null, e.getStartsAt(), e.getEndsAt(), EventMetric.LENGTH, "Hauki", 2);
        CompetitionEvent changed = events.find(e.getId()).orElseThrow();
        assertEquals("Uusi nimi", changed.getName());
        assertEquals(EventMetric.LENGTH, changed.getMetric());
        assertEquals("Hauki", changed.getSpecies());
        assertEquals(2, changed.getCountedFish());

        events.delete(e.getId(), admin());
        assertTrue(events.find(e.getId()).isEmpty());
    }

    @Test
    void adminIsRecognisedByUsername_only() {
        assertTrue(events.isAdmin(admin()));
        assertFalse(events.isAdmin(newUser("Robbe")), "Pelkkä näyttönimi ei tee ylläpitäjää");
    }

    @Test
    void validation_rejectsBadInput() {
        AppUser a = admin();
        Instant s = ago(Duration.ofHours(1));
        Instant e = ahead(Duration.ofHours(1));
        assertThrows(IllegalArgumentException.class, () -> events.create(a, "  ", null, s, e, EventMetric.WEIGHT, null, 1));
        assertThrows(IllegalArgumentException.class, () -> events.create(a, "K", null, e, s, EventMetric.WEIGHT, null, 1),
                "Loppu ennen alkua");
        assertThrows(IllegalArgumentException.class, () -> events.create(a, "K", null, s, s, EventMetric.WEIGHT, null, 1));
        assertThrows(IllegalArgumentException.class, () -> events.create(a, "K", null, s, e, null, null, 1));
        assertThrows(IllegalArgumentException.class, () -> events.create(a, "K", null, null, e, EventMetric.WEIGHT, null, 1));
        assertThrows(IllegalArgumentException.class, () -> events.create(a, "K", null, s, e, EventMetric.WEIGHT, null, 0));
        assertThrows(IllegalArgumentException.class, () -> events.create(a, "K", null, s, e, EventMetric.WEIGHT, null, 11));
        assertEquals(1, events.create(a, "K", null, s, e, EventMetric.COUNT, null, 7).getCountedFish(),
                "Eniten kaloja -kilpailussa laskettavien määrä on aina 1");
        assertEquals(null, events.create(a, "K", "  ", s, e, EventMetric.WEIGHT, "  ", 1).getSpecies());
    }

    // ---- liittyminen ----

    @Test
    void anyoneCanJoin_includingTheAdmin_andJoiningTwiceIsHarmless() {
        CompetitionEvent e = ongoing(EventMetric.WEIGHT, null, 1);
        AppUser u = newUser("Kilpailija");

        events.join(e.getId(), u);
        events.join(e.getId(), u);
        events.join(e.getId(), admin());

        assertTrue(events.isParticipant(e, u));
        assertTrue(events.isParticipant(e, admin()), "Ylläpitäjä saa kilpailla itse");
        assertEquals(2, events.participantCount(e));

        events.leave(e.getId(), u);
        assertFalse(events.isParticipant(e, u));
    }

    @Test
    void endedEvent_cannotBeJoinedOrLeft_butResultsRemain() {
        CompetitionEvent e = events.create(admin(), "Mennyt", null, ago(Duration.ofDays(3)), ago(Duration.ofDays(2)),
                EventMetric.WEIGHT, null, 1);
        AppUser late = newUser("Myöhäinen");
        assertEquals(EventStatus.ENDED, e.status());
        assertThrows(IllegalArgumentException.class, () -> events.join(e.getId(), late));
        assertThrows(IllegalArgumentException.class, () -> events.leave(e.getId(), late));
        assertEquals(0, events.leaderboard(e.getId()).standings().size());
    }

    // ---- tulosten laskenta ----

    @Test
    void fishWithoutAPhoto_isNotCounted() {
        CompetitionEvent e = ongoing(EventMetric.WEIGHT, null, 1);
        AppUser u = newUser("Kilpailija");
        events.join(e.getId(), u);
        fish(u, "Hauki", 9.9, null, false, ago(Duration.ofMinutes(10)));
        fish(u, "Ahven", 1.2, null, true, ago(Duration.ofMinutes(20)));

        var standings = events.leaderboard(e.getId()).standings();
        assertEquals(1, standings.size());
        assertEquals(1.2, standings.get(0).score(), 1e-9, "Kuvaton 9,9 kg ei laske mukaan");
        assertEquals("Ahven", standings.get(0).fish().get(0).post().getSpecies());
    }

    @Test
    void timeWindow_andNonParticipants() {
        CompetitionEvent e = ongoing(EventMetric.WEIGHT, null, 1);
        AppUser in = newUser("Mukana");
        AppUser out = newUser("Ulkona");
        events.join(e.getId(), in);
        fish(in, "Hauki", 2.0, null, true, ago(Duration.ofMinutes(30)));
        fish(in, "Hauki", 8.0, null, true, ago(Duration.ofHours(3)));   // ennen alkua
        fish(out, "Hauki", 9.0, null, true, ago(Duration.ofMinutes(5))); // ei liittynyt

        var standings = events.leaderboard(e.getId()).standings();
        assertEquals(1, standings.size());
        assertEquals(in, standings.get(0).user());
        assertEquals(2.0, standings.get(0).score(), 1e-9);

        events.join(e.getId(), out); // liittyminen jälkeenpäin laskee jo pyydetyt kalat mukaan, jos ne ovat aikavälillä
        assertEquals(out, events.leaderboard(e.getId()).standings().get(0).user());
    }

    @Test
    void speciesFilter_isCaseInsensitive_andMissingMeasurementDisqualifies() {
        CompetitionEvent e = ongoing(EventMetric.WEIGHT, "Hauki", 1);
        AppUser u = newUser("Kilpailija");
        events.join(e.getId(), u);
        fish(u, "hauki", 3.0, null, true, ago(Duration.ofMinutes(30)));
        fish(u, "Ahven", 5.0, null, true, ago(Duration.ofMinutes(20)));  // väärä laji
        fish(u, "Hauki", null, 90.0, true, ago(Duration.ofMinutes(10))); // paino puuttuu

        var s = events.leaderboard(e.getId()).standings();
        assertEquals(1, s.size());
        assertEquals(3.0, s.get(0).score(), 1e-9);
        assertEquals(1, s.get(0).fish().size());
    }

    @Test
    void ranking_isByScore_andTiesGoToTheOneWhoGotThereFirst() {
        CompetitionEvent e = ongoing(EventMetric.LENGTH, null, 1);
        AppUser a = newUser("A");
        AppUser b = newUser("B");
        AppUser c = newUser("C");
        for (AppUser u : List.of(a, b, c)) events.join(e.getId(), u);
        fish(a, "Hauki", null, 80.0, true, ago(Duration.ofMinutes(10)));
        fish(b, "Hauki", null, 95.0, true, ago(Duration.ofMinutes(40)));
        fish(c, "Hauki", null, 80.0, true, ago(Duration.ofMinutes(50))); // sama tulos kuin A, mutta aiemmin

        var s = events.leaderboard(e.getId()).standings();
        assertEquals(List.of(b, c, a), s.stream().map(EventService.Standing::user).toList());
        assertEquals(List.of(1, 2, 3), s.stream().map(EventService.Standing::rank).toList());
    }

    @Test
    void countedFish_sumsTheBestNFishOfEachCompetitor() {
        CompetitionEvent e = ongoing(EventMetric.WEIGHT, null, 2);
        AppUser u = newUser("Kilpailija");
        events.join(e.getId(), u);
        fish(u, "Hauki", 5.0, null, true, ago(Duration.ofMinutes(10)));
        fish(u, "Hauki", 3.0, null, true, ago(Duration.ofMinutes(20)));
        fish(u, "Hauki", 1.0, null, true, ago(Duration.ofMinutes(30)));

        var s = events.leaderboard(e.getId()).standings().get(0);
        assertEquals(8.0, s.score(), 1e-9, "Kaksi parasta: 5 + 3");
        assertEquals(2, s.fish().size());
    }

    @Test
    void countMetric_countsValidFishOnly() {
        CompetitionEvent e = ongoing(EventMetric.COUNT, null, 1);
        AppUser u = newUser("Kilpailija");
        events.join(e.getId(), u);
        fish(u, "Hauki", null, null, true, ago(Duration.ofMinutes(10)));
        fish(u, "Ahven", null, null, true, ago(Duration.ofMinutes(20)));
        fish(u, "Kuha", null, null, false, ago(Duration.ofMinutes(30))); // ei kuvaa

        assertEquals(2.0, events.leaderboard(e.getId()).standings().get(0).score(), 1e-9);
    }

    @Test
    void theAdminCompetesLikeEveryoneElse() {
        CompetitionEvent e = ongoing(EventMetric.WEIGHT, null, 1);
        events.join(e.getId(), admin());
        fish(admin(), "Hauki", 4.0, null, true, ago(Duration.ofMinutes(10)));

        assertEquals(admin(), events.leaderboard(e.getId()).standings().get(0).user());
    }

    // ---- ylläpitäjän tarkistukset ----

    @Test
    void admin_canQuestionAFish_itStillCountsButIsFlagged_andTheQuestionGoesToTheChat() {
        CompetitionEvent e = ongoing(EventMetric.WEIGHT, null, 1);
        AppUser u = newUser("Kilpailija");
        events.join(e.getId(), u);
        CatchPost p = fish(u, "Hauki", 12.0, null, true, ago(Duration.ofMinutes(10)));

        events.review(e.getId(), p.getId(), admin(), ReviewStatus.QUESTIONED, "Onko paino oikein?");

        var s = events.leaderboard(e.getId()).standings().get(0);
        assertEquals(12.0, s.score(), 1e-9, "Kysytty kala lasketaan edelleen");
        assertTrue(s.fish().get(0).questioned());
        assertEquals("Onko paino oikein?", s.fish().get(0).review().getNote());
        var chat = events.messages(e.getId());
        assertEquals(admin(), chat.get(chat.size() - 1).getSender());
        assertTrue(chat.get(chat.size() - 1).getText().contains("onko saalis oikea"));
        assertTrue(chat.get(chat.size() - 1).getText().contains("Hauki 12 kg"));
    }

    @Test
    void admin_canRejectAFish_nextBestTakesOver_andCanRestoreIt() {
        CompetitionEvent e = ongoing(EventMetric.WEIGHT, null, 1);
        AppUser u = newUser("Kilpailija");
        events.join(e.getId(), u);
        CatchPost big = fish(u, "Hauki", 12.0, null, true, ago(Duration.ofMinutes(10)));
        fish(u, "Ahven", 1.5, null, true, ago(Duration.ofMinutes(20)));

        events.review(e.getId(), big.getId(), admin(), ReviewStatus.REJECTED, "Ei sama kala kuin kuvassa");

        var board = events.leaderboard(e.getId());
        assertEquals(1.5, board.standings().get(0).score(), 1e-9, "Seuraavaksi paras kala astuu tilalle");
        assertEquals(1, board.rejected().size());
        assertEquals(big.getId(), board.rejected().get(0).getPost().getId());

        events.clearReview(e.getId(), big.getId(), admin());
        var restored = events.leaderboard(e.getId());
        assertEquals(12.0, restored.standings().get(0).score(), 1e-9);
        assertEquals(0, restored.rejected().size());
    }

    @Test
    void reviews_areAdminOnly_andOnlyForFishThatBelongInTheResults() {
        CompetitionEvent e = ongoing(EventMetric.WEIGHT, null, 1);
        AppUser u = newUser("Kilpailija");
        events.join(e.getId(), u);
        CatchPost counted = fish(u, "Hauki", 3.0, null, true, ago(Duration.ofMinutes(10)));
        CatchPost noPhoto = fish(u, "Hauki", 9.0, null, false, ago(Duration.ofMinutes(20)));

        assertThrows(SecurityException.class,
                () -> events.review(e.getId(), counted.getId(), u, ReviewStatus.REJECTED, null));
        assertThrows(SecurityException.class, () -> events.clearReview(e.getId(), counted.getId(), u));
        assertThrows(IllegalArgumentException.class,
                () -> events.review(e.getId(), noPhoto.getId(), admin(), ReviewStatus.REJECTED, null));
        assertEquals(0, events.leaderboard(e.getId()).rejected().size());
    }

    // ---- keskustelu ----

    @Test
    void anyoneCanChat_withoutJoining_blankIsIgnored_andOrderIsOldestFirst() {
        CompetitionEvent e = ongoing(EventMetric.WEIGHT, null, 1);
        AppUser outsider = newUser("Vierailija");

        events.postMessage(e.getId(), outsider, "  Moi kaikki  ");
        events.postMessage(e.getId(), admin(), "Tervetuloa!");
        events.postMessage(e.getId(), outsider, "   ");
        assertThrows(IllegalArgumentException.class, () -> events.postMessage(e.getId(), outsider, "x".repeat(1001)));

        var chat = events.messages(e.getId());
        assertEquals(2, chat.size());
        assertEquals("Moi kaikki", chat.get(0).getText());
        assertEquals("Tervetuloa!", chat.get(1).getText());
    }

    // ---- siivous ja suojaukset ----

    @Test
    void deletingTheEvent_removesParticipantsMessagesAndReviews_butKeepsTheFish() {
        CompetitionEvent e = ongoing(EventMetric.WEIGHT, null, 1);
        AppUser u = newUser("Kilpailija");
        events.join(e.getId(), u);
        CatchPost p = fish(u, "Hauki", 3.0, null, true, ago(Duration.ofMinutes(10)));
        events.review(e.getId(), p.getId(), admin(), ReviewStatus.QUESTIONED, null);
        events.postMessage(e.getId(), u, "Moi");

        events.delete(e.getId(), admin());

        assertTrue(events.find(e.getId()).isEmpty());
        assertEquals(1, posts.byAuthor(u).size(), "Saaliit säilyvät");
    }

    @Test
    void deletingAFish_removesItsReviews() {
        CompetitionEvent e = ongoing(EventMetric.WEIGHT, null, 1);
        AppUser u = newUser("Kilpailija");
        events.join(e.getId(), u);
        CatchPost p = fish(u, "Hauki", 3.0, null, true, ago(Duration.ofMinutes(10)));
        events.review(e.getId(), p.getId(), admin(), ReviewStatus.REJECTED, null);

        posts.delete(p.getId(), u);

        assertEquals(0, events.leaderboard(e.getId()).rejected().size());
    }

    @Test
    void deletingAnAccount_removesItsParticipationAndMessages() {
        CompetitionEvent e = ongoing(EventMetric.WEIGHT, null, 1);
        AppUser u = newUser("Poistuja");
        events.join(e.getId(), u);
        events.postMessage(e.getId(), u, "Hei");

        accounts.deleteAccount(u.getId(), PASSWORD);

        assertEquals(0, events.participantCount(e));
        assertEquals(0, events.messages(e.getId()).size());
    }

    @Test
    void adminAccount_cannotBeDeletedOrRenamed_andNobodyElseCanTakeTheUsername() {
        AppUser a = admin();
        assertThrows(IllegalArgumentException.class, () -> accounts.deleteAccount(a.getId(), PASSWORD));
        assertThrows(IllegalArgumentException.class,
                () -> users.updateAccount(a.getId(), a.getDisplayName(), "joku-muu", PASSWORD, ""));

        AppUser other = newUser("Muu");
        assertThrows(IllegalArgumentException.class,
                () -> users.updateAccount(other.getId(), other.getDisplayName(), "robbe", PASSWORD, ""),
                "Ylläpitäjän tunnus on varattu");
        assertEquals("robbe", users.find("robbe").orElseThrow().getUsername());
        // Näyttönimen vaihto ylläpitäjälle on silti sallittu
        assertFalse(users.updateAccount(a.getId(), "Robbe Uusi", "robbe", "", ""));
    }
}
