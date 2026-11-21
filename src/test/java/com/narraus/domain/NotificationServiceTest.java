package com.narraus.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.narraus.NarrausTest;
import com.narraus.domain.NotificationService.Entry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Kellokuvakkeen ilmoitukset: uusi seuraaja, tykkäys, uusi kilpailu ja Narraus-viestit samassa listassa. */
@NarrausTest
class NotificationServiceTest {

    @Autowired
    private NotificationService notifications;
    @Autowired
    private UserService users;
    @Autowired
    private PostService posts;
    @Autowired
    private EventService events;
    @Autowired
    private SystemMessageService system;
    @Autowired
    private AccountService accounts;

    private AppUser admin() {
        return users.find("robbe").orElseGet(() -> users.register("robbe", "Robbe Ylläpitäjä", "salasana123"));
    }

    private AppUser newUser(String prefix) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        return users.register("u-" + id, prefix + "-" + id, "salasana123");
    }

    @Test
    void newFollower_notifiesTheFollowedUser_once() {
        AppUser me = newUser("Minä");
        AppUser fan = newUser("Fani");

        users.toggleFollow(fan, me);
        assertEquals(1, notifications.unreadCount(me));
        Entry e = notifications.recent(me).get(0);
        assertEquals(NotificationType.FOLLOWER, e.kind());
        assertEquals(fan.getId(), e.actor().getId());
        assertTrue(e.unread());

        users.toggleFollow(fan, me); // lopettaa seuraamisen
        users.toggleFollow(fan, me); // seuraa uudelleen heti: ei uutta ilmoitusta
        assertEquals(1, notifications.recent(me).size(), "Toistuvasta seuraamisesta ei ilmoiteta uudelleen");
        assertEquals(0, notifications.recent(fan).size(), "Seuraaja itse ei saa ilmoitusta");
    }

    @Test
    void like_notifiesTheAuthor_butNotOnOwnLike_andOnlyOncePerPersonAndPost() {
        AppUser author = newUser("Kalastaja");
        AppUser fan = newUser("Fani");
        CatchPost post = posts.create(author, "Hauki", null, null, null, null, null, null, null);

        posts.toggleLike(post, author); // oma tykkäys
        assertEquals(0, notifications.unreadCount(author));

        posts.toggleLike(post, fan);
        Entry e = notifications.recent(author).get(0);
        assertEquals(NotificationType.LIKE, e.kind());
        assertEquals(post.getId(), e.targetId());
        assertEquals("Hauki", e.label());

        posts.toggleLike(post, fan); // peruu
        posts.toggleLike(post, fan); // tykkää uudelleen
        assertEquals(1, notifications.recent(author).size());
    }

    @Test
    void newEvent_notifiesEveryoneExceptTheCreator() {
        AppUser a = newUser("A");
        AppUser b = newUser("B");
        AppUser admin = admin();

        CompetitionEvent ev = events.create(admin, "Kisa " + UUID.randomUUID().toString().substring(0, 6), null,
                Instant.now().minus(Duration.ofHours(1)), Instant.now().plus(Duration.ofDays(1)),
                EventMetric.WEIGHT, null, 1);

        for (AppUser u : List.of(a, b)) {
            Entry e = notifications.recent(u).get(0);
            assertEquals(NotificationType.EVENT, e.kind());
            assertEquals(ev.getId(), e.targetId());
            assertEquals(ev.getName(), e.label());
        }
        assertTrue(notifications.recent(admin).stream().noneMatch(e -> e.kind() == NotificationType.EVENT
                && ev.getId().equals(e.targetId())), "Luoja ei saa ilmoitusta omasta kilpailustaan");
    }

    @Test
    void narrausMessages_areInTheSameList_andStayInTheirOwnPlaceToo() {
        AppUser me = newUser("Minä");
        AppUser fan = newUser("Fani");
        users.toggleFollow(fan, me);
        system.send(admin(), Set.of(me), "Huoltokatko");

        List<Entry> list = notifications.recent(me);

        assertEquals(2, list.size());
        assertTrue(list.stream().anyMatch(e -> e.systemMessage() && "Huoltokatko".equals(e.label())));
        assertEquals(1, system.messagesFor(me).size(), "Narraus-viesti on edelleen Keskusteluissa");
        assertEquals(2, notifications.unreadCount(me));
    }

    @Test
    void list_isNewestFirst() {
        AppUser me = newUser("Minä");
        AppUser first = newUser("Eka");
        AppUser second = newUser("Toka");
        users.toggleFollow(first, me);
        users.toggleFollow(second, me);

        List<Entry> list = notifications.recent(me);

        assertEquals(second.getId(), list.get(0).actor().getId());
        assertEquals(first.getId(), list.get(1).actor().getId());
    }

    @Test
    void markAllRead_clearsTheBadge_includingNarrausMessages() {
        AppUser me = newUser("Minä");
        users.toggleFollow(newUser("Fani"), me);
        system.send(admin(), Set.of(me), "Viesti");
        assertEquals(2, notifications.unreadCount(me));

        assertTrue(notifications.markAllRead(me));

        assertEquals(0, notifications.unreadCount(me));
        assertEquals(0, system.unreadCount(me));
        assertTrue(notifications.recent(me).stream().noneMatch(Entry::unread));
        assertFalse(notifications.markAllRead(me), "Toisella kerralla ei enää muutu mikään");
    }

    @Test
    void deletingAnAccount_removesItsNotifications_bothReceivedAndCaused() {
        AppUser me = newUser("Minä");
        AppUser leaving = newUser("Lähtijä");
        users.toggleFollow(leaving, me); // me sai ilmoituksen lähtijältä
        users.toggleFollow(me, leaving); // lähtijä sai ilmoituksen mena

        accounts.deleteAccount(leaving.getId(), "salasana123"); // ei saa kaatua viiteavaimeen

        assertEquals(0, notifications.recent(me).size(), "Poistuneen aiheuttamat ilmoitukset poistuvat");
    }
}
