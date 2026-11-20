package com.narraus.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.narraus.NarrausTest;
import com.vaadin.flow.server.webpush.WebPushKeys;
import com.vaadin.flow.server.webpush.WebPushSubscription;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/** Tilin poisto: poistaa käyttäjän omat tiedot kokonaan, mutta ei kenenkään muun tietoja. */
@NarrausTest
class AccountDeletionTest {

    private static final String PASSWORD = "salasana123";
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3, 4};

    @Autowired
    private AccountService accounts;
    @Autowired
    private UserService users;
    @Autowired
    private PostService posts;
    @Autowired
    private ChatService chats;
    @Autowired
    private PushService push;
    @Autowired
    private UnreadService unread;

    private AppUser newUser(String prefix) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        return users.register("u-" + id, prefix + "-" + id, PASSWORD);
    }

    private boolean photoExists(String file) {
        try (var in = posts.openPhoto(file)) {
            return in != null;
        } catch (UncheckedIOException | IOException e) {
            return false;
        }
    }

    // ---- salasanan vahvistus ----

    @Test
    void wrongPassword_isRejected_andNothingIsDeleted() {
        AppUser me = newUser("Minä");
        posts.create(me, "Hauki", null, null, null, null, null, PNG, "image/png");

        assertThrows(IllegalArgumentException.class, () -> accounts.deleteAccount(me.getId(), "vaara-salasana"));
        assertThrows(IllegalArgumentException.class, () -> accounts.deleteAccount(me.getId(), ""));
        assertThrows(IllegalArgumentException.class, () -> accounts.deleteAccount(me.getId(), null));

        assertTrue(users.find(me.getUsername()).isPresent(), "Tili säilyy");
        assertEquals(1, posts.postCount(me), "Julkaisut säilyvät");
        assertEquals(1, posts.byAuthor(me).size());
        assertTrue(photoExists(posts.byAuthor(me).get(0).getPhotoFile()), "Kuva säilyy");
    }

    @Test
    void anotherUsersPassword_cannotDeleteSomeoneElsesAccount() {
        AppUser victim = newUser("Uhri");
        AppUser attacker = newUser("Hyökkääjä");
        posts.create(victim, "Hauki", null, null, null, null, null, null, null);

        assertThrows(IllegalArgumentException.class, () -> accounts.deleteAccount(victim.getId(), PASSWORD + "x"));
        // Hyökkääjän oma salasana on sama kuin testissä, mutta hash on eri: oikea salasana vaaditaan juuri tälle tilille
        accounts.deleteAccount(attacker.getId(), PASSWORD);

        assertTrue(users.find(victim.getUsername()).isPresent());
        assertEquals(1, posts.postCount(victim));
        assertFalse(users.find(attacker.getUsername()).isPresent());
    }

    @Test
    void unknownUser_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> accounts.deleteAccount(987654321L, PASSWORD));
        assertThrows(IllegalArgumentException.class, () -> accounts.deleteAccount(null, PASSWORD));
    }

    // ---- oma data poistuu ----

    @Test
    void deletingTheAccount_removesTheUser_andLoginNoLongerWorks() {
        AppUser me = newUser("Minä");

        accounts.deleteAccount(me.getId(), PASSWORD);

        assertTrue(users.find(me.getUsername()).isEmpty());
        assertTrue(users.findById(me.getId()).isEmpty());
        assertThrows(UsernameNotFoundException.class, () -> users.loadUserByUsername(me.getUsername()));
        assertTrue(users.search(me.getDisplayName()).isEmpty(), "Poistettu käyttäjä ei löydy haulla");
    }

    @Test
    void ownPosts_areDeletedWithPhotosAndLikes_andLeaveTheFeedAndMap() {
        AppUser me = newUser("Minä");
        AppUser fan = newUser("Fani");
        CatchPost withPhoto = posts.create(me, "Hauki", null, null, null, null, null, PNG, "image/png", 61.5, 25.5);
        CatchPost plain = posts.create(me, "Ahven", null, null, null, null, null, null, null);
        posts.toggleLike(withPhoto, fan);
        posts.toggleLike(plain, me);
        String file = withPhoto.getPhotoFile();
        assertTrue(photoExists(file));

        accounts.deleteAccount(me.getId(), PASSWORD);

        assertFalse(photoExists(file), "Kuvatiedosto poistuu levyltä");
        var feedIds = posts.feed(fan, false).stream().map(CatchPost::getId).toList();
        assertFalse(feedIds.contains(withPhoto.getId()));
        assertFalse(feedIds.contains(plain.getId()));
        assertFalse(posts.withLocation().stream().anyMatch(p -> p.getId().equals(withPhoto.getId())));
        assertEquals(0, posts.postCount(fan), "Fanin omat tiedot eivät muutu");
    }

    @Test
    void likesGivenToOthers_areRemoved_butTheOthersPostsStay() {
        AppUser me = newUser("Minä");
        AppUser other = newUser("Toinen");
        CatchPost post = posts.create(other, "Kuha", null, null, null, null, null, null, null);
        posts.toggleLike(post, me);
        posts.toggleLike(post, other);
        assertEquals(2, posts.likeCount(post));

        accounts.deleteAccount(me.getId(), PASSWORD);

        assertEquals(1, posts.postCount(other), "Toisen julkaisu säilyy");
        assertEquals(1, posts.likeCount(post), "Vain poistetun käyttäjän tykkäys poistuu");
    }

    @Test
    void follows_areRemovedInBothDirections() {
        AppUser me = newUser("Minä");
        AppUser iFollow = newUser("Seurattava");
        AppUser followsMe = newUser("Seuraaja");
        users.toggleFollow(me, iFollow);
        users.toggleFollow(followsMe, me);
        assertEquals(1, users.followerCount(iFollow));
        assertEquals(1, users.followingCount(followsMe));

        accounts.deleteAccount(me.getId(), PASSWORD);

        assertEquals(0, users.followerCount(iFollow), "Seuraajamäärä pienenee");
        assertEquals(0, users.followingCount(followsMe), "Seurattavien määrä pienenee");
    }

    @Test
    void pushDevices_areRemoved() {
        AppUser me = newUser("Minä");
        for (int i = 0; i < 2; i++) {
            push.subscribe(me, new WebPushSubscription("https://push.example.test/" + UUID.randomUUID(),
                    new WebPushKeys("k", "a")));
        }
        assertEquals(2, push.deviceCount(me));

        accounts.deleteAccount(me.getId(), PASSWORD);

        // Tilaukset on poistettu: tietokantarivit eivät enää viittaa poistettuun käyttäjään (ei viiteavainvirhettä)
        assertTrue(users.findById(me.getId()).isEmpty());
    }

    // ---- keskustelut ----

    @Test
    void directConversations_areDeletedEntirely() {
        AppUser me = newUser("Minä");
        AppUser friend = newUser("Kaveri");
        Conversation c = chats.openDirect(me, friend);
        chats.send(c, me, "oma viesti");
        chats.send(c, friend, "kaverin viesti");

        accounts.deleteAccount(me.getId(), PASSWORD);

        assertTrue(chats.conversationsOf(friend).isEmpty(), "Suora keskustelu poistuu myös kaverilta");
        assertEquals(0, unread.unreadMessages(friend));
    }

    @Test
    void groups_loseTheUsersMessagesAndMembership_butKeepOthersMessages() {
        AppUser me = newUser("Minä");
        AppUser a = newUser("A");
        AppUser b = newUser("B");
        String name = "Porukka-" + UUID.randomUUID().toString().substring(0, 6);
        Conversation g = chats.createGroup(me, name, Set.of(a, b));
        chats.send(g, me, "minun viestini");
        chats.send(g, a, "A:n viesti");
        chats.send(g, b, "B:n viesti");

        accounts.deleteAccount(me.getId(), PASSWORD);

        Conversation after = chats.get(g.getId(), a);
        assertEquals(name, after.getName(), "Ryhmä säilyy muille");
        assertEquals(Set.of(a, b), after.getParticipants(), "Poistettu jäsen on poissa");
        List<String> texts = chats.messages(after, a).stream().map(ChatMessage::getText).toList();
        assertEquals(List.of("A:n viesti", "B:n viesti"), texts, "Poistetun viestit poistuvat, muiden säilyvät");
    }

    @Test
    void groupThatBecomesEmpty_isDeleted() {
        AppUser me = newUser("Minä");
        Conversation g = chats.createGroup(me, "Yksin-" + UUID.randomUUID(), Set.of());
        chats.send(g, me, "kukaan ei kuule");

        accounts.deleteAccount(me.getId(), PASSWORD);

        assertTrue(chats.conversationsOf(me).isEmpty());
        assertThrows(SecurityException.class, () -> chats.updateGroup(g.getId(), me, "Uusi", Set.of()),
                "Tyhjentynyt ryhmä on poistettu eikä sitä voi enää muokata");
    }

    @Test
    void unreadCounts_ofOthers_stayConsistent() {
        AppUser me = newUser("Minä");
        AppUser a = newUser("A");
        AppUser b = newUser("B");
        Conversation g = chats.createGroup(me, "Porukka-" + UUID.randomUUID(), Set.of(a, b));
        chats.send(g, me, "poistuvan viesti");
        chats.send(g, b, "B:n viesti");
        assertEquals(2, unread.unreadMessages(a));

        accounts.deleteAccount(me.getId(), PASSWORD);

        assertEquals(1, unread.unreadMessages(a), "Poistetun viesti ei jää lukemattomaksi");
    }

    // ---- muiden data säilyy ----

    @Test
    void otherUsersData_isUntouched() {
        AppUser me = newUser("Minä");
        AppUser other = newUser("Toinen");
        CatchPost othersPost = posts.create(other, "Taimen", 2.0, null, null, "Tenojoki", "Hieno", PNG, "image/png",
                69.9, 27.0);
        String othersPhoto = othersPost.getPhotoFile();
        AppUser third = newUser("Kolmas");
        users.toggleFollow(other, third);
        Conversation othersChat = chats.openDirect(other, third);
        chats.send(othersChat, other, "ei liity poistettavaan");
        posts.create(me, "Poistuva", null, null, null, null, null, PNG, "image/png");

        accounts.deleteAccount(me.getId(), PASSWORD);

        assertEquals(1, posts.postCount(other));
        assertTrue(photoExists(othersPhoto), "Toisen kuva säilyy");
        assertEquals(1, users.followerCount(third));
        assertEquals(1, chats.messages(chats.get(othersChat.getId(), third), third).size());
    }
}
