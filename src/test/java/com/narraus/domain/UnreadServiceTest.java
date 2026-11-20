package com.narraus.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.narraus.NarrausTest;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@NarrausTest
class UnreadServiceTest {

    @Autowired
    private UnreadService unread;
    @Autowired
    private UserService users;
    @Autowired
    private PostService posts;
    @Autowired
    private ChatService chats;

    private AppUser newUser(String prefix) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        return users.register("u-" + id, prefix + "-" + id, "salasana123");
    }

    private static void pause() throws InterruptedException {
        Thread.sleep(15); // varmistaa, että aikaleimat ovat selvästi eri hetkiä
    }

    @Test
    void newPosts_countsOthersPostsSinceFeedWasSeen_notOwn() throws Exception {
        AppUser me = newUser("Minä");
        AppUser other = newUser("Toinen");
        pause();

        posts.create(other, "Hauki", null, null, null, null, null, null, null);
        posts.create(other, "Ahven", null, null, null, null, null, null, null);
        posts.create(me, "OmaKuha", null, null, null, null, null, null, null);

        assertEquals(2, unread.newPosts(me), "Vain toisten julkaisut lasketaan");
        assertEquals(1, unread.newPosts(other), "Toiselle uusi on vain minun julkaisu");
    }

    @Test
    void markFeedSeen_resetsCount_andLaterPostsCountAgain() throws Exception {
        AppUser me = newUser("Minä");
        AppUser other = newUser("Toinen");
        pause();
        posts.create(other, "Hauki", null, null, null, null, null, null, null);
        assertEquals(1, unread.newPosts(me));

        pause();
        unread.markFeedSeen(me);
        assertEquals(0, unread.newPosts(me));

        pause();
        posts.create(other, "Ahven", null, null, null, null, null, null, null);
        assertEquals(1, unread.newPosts(me));
    }

    @Test
    void newUser_doesNotSeeOldPostsAsNew() throws Exception {
        AppUser author = newUser("Kirjoittaja");
        posts.create(author, "Vanha", null, null, null, null, null, null, null);
        pause();

        AppUser latecomer = newUser("Myöhäinen");
        assertEquals(0, unread.newPosts(latecomer));
    }

    @Test
    void unreadMessages_countsOthersMessages_andMarkReadClearsThem() throws Exception {
        AppUser me = newUser("Minä");
        AppUser friend = newUser("Kaveri");
        Conversation c = chats.openDirect(me, friend);

        chats.send(c, friend, "yksi");
        chats.send(c, friend, "kaksi");
        chats.send(c, me, "oma viesti");

        assertEquals(2, unread.unreadMessages(me), "Omia viestejä ei lasketa");
        assertEquals(1, unread.unreadMessages(friend), "Kaverille lukematon on vain minun viestini");

        pause();
        assertTrue(unread.markRead(c, me), "Ensimmäisellä kerralla lukemattomia oli");
        assertEquals(0, unread.unreadMessages(me));
        assertEquals(1, unread.unreadMessages(friend), "Toisen lukemattomat eivät muutu");
        assertFalse(unread.markRead(c, me), "Toisella kerralla ei enää lukemattomia");

        pause();
        chats.send(c, friend, "kolme");
        assertEquals(1, unread.unreadMessages(me));
    }

    @Test
    void unreadMessages_sumsAcrossConversations() throws Exception {
        AppUser me = newUser("Minä");
        AppUser a = newUser("A");
        AppUser b = newUser("B");
        Conversation ca = chats.openDirect(me, a);
        Conversation group = chats.createGroup(a, "Porukka-" + UUID.randomUUID(), Set.of(me, b));

        chats.send(ca, a, "hei");
        chats.send(group, b, "hei kaikki");
        chats.send(group, a, "moi");

        assertEquals(3, unread.unreadMessages(me));
        pause();
        unread.markRead(group, me);
        assertEquals(1, unread.unreadMessages(me));
    }

    @Test
    void conversationOfOthers_neverCountsForMe() {
        AppUser me = newUser("Ulkopuolinen");
        AppUser a = newUser("A");
        AppUser b = newUser("B");
        Conversation c = chats.openDirect(a, b);
        chats.send(c, a, "salaisuus");

        assertEquals(0, unread.unreadMessages(me));
        assertEquals(List.of(), chats.conversationsOf(me));
    }

    @Test
    void invalidCoordinates_areRejected() {
        AppUser me = newUser("Minä");
        assertThrows(IllegalArgumentException.class, () ->
                posts.create(me, "Hauki", null, null, null, null, null, null, null, 95.0, 25.0));
        assertThrows(IllegalArgumentException.class, () ->
                posts.create(me, "Hauki", null, null, null, null, null, null, null, 60.0, 190.0));
    }

    @Test
    void withLocation_returnsOnlyPostsWithCoordinates() {
        AppUser me = newUser("Minä");
        CatchPost located = posts.create(me, "Hauki", null, null, null, null, null, null, null, 61.5, 25.5);
        CatchPost plain = posts.create(me, "Ahven", null, null, null, "Jossain", null, null, null);

        var ids = posts.withLocation().stream().map(CatchPost::getId).toList();
        assertTrue(ids.contains(located.getId()));
        assertFalse(ids.contains(plain.getId()));
        assertEquals(61.5, located.getLatitude());
        assertEquals(25.5, located.getLongitude());
    }
}
