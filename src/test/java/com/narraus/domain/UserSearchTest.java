package com.narraus.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.narraus.NarrausTest;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Käyttäjähaku sivuttain (ryhmän jäsenvalinta): toimii myös, kun käyttäjiä on yli 50. */
@NarrausTest
class UserSearchTest {

    @Autowired
    private UserService users;

    private AppUser newUser(String displayName) {
        return users.register("u-" + UUID.randomUUID().toString().substring(0, 12), displayName, "salasana123");
    }

    @Test
    void searchOthers_pagesThroughMoreThanFiftyUsers() {
        String tag = "Massa" + UUID.randomUUID().toString().substring(0, 8);
        AppUser me = newUser(tag + "-minä");
        IntStream.rangeClosed(1, 60).forEach(i -> newUser(String.format("%s-%02d", tag, i)));

        assertEquals(60, users.countOthers(tag, me), "Kaikki 60 muuta löytyvät, itseä ei lasketa");
        List<AppUser> first = users.searchOthers(tag, me, 0, 50);
        List<AppUser> second = users.searchOthers(tag, me, 50, 50);
        assertEquals(50, first.size());
        assertEquals(10, second.size(), "Toinen sivu sisältää loput: yli 50 käyttäjää ei jää piiloon");
        assertEquals(60, java.util.stream.Stream.concat(first.stream(), second.stream()).distinct().count(),
                "Sivut eivät mene päällekkäin");
        assertFalse(first.contains(me) || second.contains(me));
    }

    @Test
    void searchOthers_isSortedByDisplayName_andFiltersByNameFragment() {
        String tag = "Sorttaus" + UUID.randomUUID().toString().substring(0, 8);
        AppUser me = newUser(tag + "-minä");
        newUser(tag + "-c");
        newUser(tag + "-a");
        newUser(tag + "-b");
        newUser("Aivan-eri-nimi-" + UUID.randomUUID());

        List<String> names = users.searchOthers(tag.toUpperCase(), me, 0, 50).stream()
                .map(AppUser::getDisplayName).toList();

        assertEquals(List.of(tag + "-a", tag + "-b", tag + "-c"), names, "Aakkosjärjestys, kirjainkoolla ei ole väliä");
    }

    @Test
    void searchOthers_worksWithAnyOffset_notJustWholePages() {
        String tag = "Offset" + UUID.randomUUID().toString().substring(0, 8);
        AppUser me = newUser(tag + "-minä");
        IntStream.rangeClosed(1, 25).forEach(i -> newUser(String.format("%s-%02d", tag, i)));

        List<AppUser> window = users.searchOthers(tag, me, 7, 10);

        assertEquals(10, window.size());
        assertEquals(tag + "-08", window.get(0).getDisplayName(), "Offset 7 alkaa kahdeksannesta käyttäjästä");
        assertEquals(tag + "-17", window.get(9).getDisplayName());
        assertTrue(users.searchOthers(tag, me, 100, 10).isEmpty(), "Offset yli loppun palauttaa tyhjän");
        assertTrue(users.searchOthers(tag, me, 0, 0).isEmpty(), "Nollakoko palauttaa tyhjän");
    }

    @Test
    void searchOthers_blankFilterListsEveryoneElse_andNeverMatchesUsernames() {
        AppUser me = newUser("Hakija-" + UUID.randomUUID());
        AppUser other = newUser("Kohde-" + UUID.randomUUID());

        assertTrue(users.countOthers("", me) >= 1);
        assertTrue(users.countOthers(null, me) >= 1);
        assertEquals(0, users.countOthers(other.getUsername(), me), "Tunnuksella ei löydy");
        assertTrue(users.searchOthers(other.getUsername(), me, 0, 50).isEmpty());
    }
}
