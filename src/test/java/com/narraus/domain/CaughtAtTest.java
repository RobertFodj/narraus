package com.narraus.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.narraus.NarrausTest;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@NarrausTest
class CaughtAtTest {

    @Autowired
    private UserService users;
    @Autowired
    private PostService posts;

    private AppUser newUser() {
        String id = UUID.randomUUID().toString().substring(0, 8);
        return users.register("u-" + id, "Kalastaja-" + id, "salasana123");
    }

    private CatchPost create(AppUser me, String species, Instant caughtAt) {
        return posts.create(me, species, null, null, null, null, null, null, null, null, null, caughtAt);
    }

    @Test
    void withoutATime_theCatchIsFromNow() {
        Instant before = Instant.now();
        CatchPost p = create(newUser(), "Hauki", null);
        assertTrue(!p.getCaughtAt().isBefore(before.minusSeconds(1)) && !p.getCaughtAt().isAfter(Instant.now().plusSeconds(1)));
    }

    @Test
    void anEarlierTime_isStored_andShownInsteadOfThePublishTime() {
        Instant yesterday = Instant.now().minus(Duration.ofDays(1)).minus(Duration.ofHours(3)).truncatedTo(ChronoUnit.SECONDS);
        CatchPost p = create(newUser(), "Ahven", yesterday);

        assertEquals(yesterday, p.getCaughtAt());
        assertTrue(p.getCreatedAt().isAfter(p.getCaughtAt()), "Julkaisuhetki säilyy erillään pyyntiajasta");
    }

    @Test
    void aFutureTime_isRejected_butFiveMinutesAheadIsTolerated() {
        AppUser me = newUser();
        assertThrows(IllegalArgumentException.class, () -> create(me, "Kuha", Instant.now().plus(Duration.ofDays(1))));
        create(me, "Kuha", Instant.now().plus(Duration.ofMinutes(2)));
        assertEquals(1, posts.postCount(me));
    }

    @Test
    void editing_changesTheTime_andNullKeepsIt() {
        AppUser me = newUser();
        Instant original = Instant.now().minus(Duration.ofHours(5)).truncatedTo(ChronoUnit.SECONDS);
        CatchPost p = create(me, "Hauki", original);

        Instant changed = original.minus(Duration.ofDays(2));
        CatchPost edited = posts.update(p.getId(), me, "Hauki", null, null, null, null, null, null, null, false,
                null, null, changed);
        assertEquals(changed, edited.getCaughtAt());

        CatchPost kept = posts.update(p.getId(), me, "Hauki", null, null, null, null, null, null, null, false,
                null, null);
        assertEquals(changed, kept.getCaughtAt(), "Ilman uutta aikaa vanha säilyy");

        assertThrows(IllegalArgumentException.class, () -> posts.update(p.getId(), me, "Hauki", null, null, null, null,
                null, null, null, false, null, null, Instant.now().plus(Duration.ofDays(3))));
        assertEquals(changed, posts.byAuthor(me).get(0).getCaughtAt(), "Virheellinen aika ei muuta mitään");
    }

    @Test
    void feedAndProfile_areOrderedByTheCatchTime() {
        AppUser me = newUser();
        CatchPost newer = create(me, "Uudempi", Instant.now().minus(Duration.ofHours(1)));
        CatchPost olderButPublishedLater = create(me, "Vanhempi", Instant.now().minus(Duration.ofDays(3)));

        var mine = posts.byAuthor(me);
        assertEquals(newer.getId(), mine.get(0).getId(), "Uudemmin pyydetty ensin, vaikka toinen julkaistiin myöhemmin");
        assertEquals(olderButPublishedLater.getId(), mine.get(1).getId());
    }
}
