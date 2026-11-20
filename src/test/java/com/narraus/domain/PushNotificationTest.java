package com.narraus.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.narraus.NarrausTest;
import com.narraus.RecordingPushSender;
import com.vaadin.flow.server.webpush.WebPushException;
import com.vaadin.flow.server.webpush.WebPushKeys;
import com.vaadin.flow.server.webpush.WebPushSubscription;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Push-ilmoitukset: laitetilaukset sekä viesti- ja julkaisuilmoitusten vastaanottajat. */
@NarrausTest
class PushNotificationTest {

    @Autowired
    private PushService push;
    @Autowired
    private UserService users;
    @Autowired
    private ChatService chats;
    @Autowired
    private PostService posts;
    @Autowired
    private RecordingPushSender sender;
    @Autowired
    private PushSubscriptionRepository subscriptions;

    private AppUser newUser(String prefix) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        return users.register("u-" + id, prefix + "-" + id, "salasana123");
    }

    /** Tilaa laitteen käyttäjälle ja palauttaa laitteen osoitteen (endpoint). */
    private String subscribeDevice(AppUser user) {
        String endpoint = "https://push.example.test/" + UUID.randomUUID();
        push.subscribe(user, new WebPushSubscription(endpoint, new WebPushKeys("p256dh-key", "auth-key")));
        return endpoint;
    }

    // ---- tilaukset ----

    @Test
    void pushIsEnabled_whenKeysAreConfigured() {
        assertTrue(push.isEnabled());
        assertFalse(push.publicKey().isBlank());
        assertEquals(87, push.publicKey().length(), "Julkinen avain on 65 tavua base64url-koodattuna (87 merkkiä)");
    }

    @Test
    void pushIsDisabled_withoutKeys() {
        var off = new PushService("", "", "https://example.test", subscriptions);
        assertFalse(off.isEnabled());
        assertThrows(IllegalStateException.class, off::webPush);
        assertFalse(new PushService("pub", "  ", "https://example.test", subscriptions).isEnabled());
    }

    @Test
    void subscribe_storesTheDevice_andCountsPerUser() {
        AppUser me = newUser("Minä");
        assertEquals(0, push.deviceCount(me));

        String phone = subscribeDevice(me);
        String laptop = subscribeDevice(me);

        assertEquals(2, push.deviceCount(me));
        assertTrue(push.isSubscribed(me, phone));
        assertTrue(push.isSubscribed(me, laptop));
    }

    @Test
    void subscribe_sameDeviceTwice_isStoredOnce() {
        AppUser me = newUser("Minä");
        String endpoint = subscribeDevice(me);

        push.subscribe(me, new WebPushSubscription(endpoint, new WebPushKeys("uusi-avain", "uusi-auth")));

        assertEquals(1, push.deviceCount(me));
        assertEquals("uusi-avain", subscriptions.findByEndpoint(endpoint).orElseThrow().getP256dh(),
                "Avaimet päivittyvät, kun selain uusii tilauksen");
    }

    @Test
    void subscribe_deviceMovesToTheUserWhoLoggedInLast() {
        AppUser first = newUser("Ensimmäinen");
        AppUser second = newUser("Toinen");
        String shared = subscribeDevice(first);

        push.subscribe(second, new WebPushSubscription(shared, new WebPushKeys("k", "a")));

        assertFalse(push.isSubscribed(first, shared));
        assertTrue(push.isSubscribed(second, shared));
        assertEquals(0, push.deviceCount(first));
        assertEquals(1, push.deviceCount(second));
    }

    @Test
    void subscribe_withInvalidData_isRejected() {
        AppUser me = newUser("Minä");
        assertThrows(IllegalArgumentException.class, () -> push.subscribe(me, null));
        assertThrows(IllegalArgumentException.class, () -> push.subscribe(null,
                new WebPushSubscription("https://push.example.test/x", new WebPushKeys("k", "a"))));
        assertThrows(IllegalArgumentException.class, () -> push.subscribe(me, new WebPushSubscription(null, null)));
    }

    @Test
    void unsubscribe_removesOnlyTheUsersOwnDevice() {
        AppUser me = newUser("Minä");
        AppUser other = newUser("Toinen");
        String mine = subscribeDevice(me);
        String theirs = subscribeDevice(other);

        push.unsubscribe(me, theirs); // ei saa poistaa toisen laitetta
        assertTrue(push.isSubscribed(other, theirs));

        push.unsubscribe(me, mine);
        assertFalse(push.isSubscribed(me, mine));
        assertEquals(1, push.deviceCount(other));
    }

    // ---- viesti-ilmoitukset ----

    @Test
    void message_notifiesTheOtherParticipantsDevices_notTheSender() {
        AppUser me = newUser("Lähettäjä");
        AppUser friend = newUser("Kaveri");
        String friendPhone = subscribeDevice(friend);
        String friendLaptop = subscribeDevice(friend);
        String myPhone = subscribeDevice(me);
        Conversation c = chats.openDirect(me, friend);

        chats.send(c, me, "Moi, lähdetäänkö kalaan?");

        assertEquals(1, sender.sentTo(friendPhone).size(), "Kaverin puhelin saa ilmoituksen");
        assertEquals(1, sender.sentTo(friendLaptop).size(), "Ja tietokone");
        assertEquals(0, sender.sentTo(myPhone).size(), "Lähettäjä ei saa ilmoitusta omasta viestistään");
        var n = sender.sentTo(friendPhone).get(0);
        assertEquals(me.getDisplayName(), n.title(), "Suorassa keskustelussa otsikkona on lähettäjän nimi");
        assertEquals("Moi, lähdetäänkö kalaan?", n.body());
    }

    @Test
    void groupMessage_showsGroupNameAndSender_andReachesEveryoneExceptTheSender() {
        AppUser me = newUser("Lähettäjä");
        AppUser a = newUser("A");
        AppUser b = newUser("B");
        String aDevice = subscribeDevice(a);
        String bDevice = subscribeDevice(b);
        String myDevice = subscribeDevice(me);
        String groupName = "Porukka-" + UUID.randomUUID().toString().substring(0, 6);
        Conversation g = chats.createGroup(me, groupName, Set.of(a, b));

        chats.send(g, me, "Huomenna kello 6");

        assertEquals(groupName, sender.sentTo(aDevice).get(0).title());
        assertEquals(me.getDisplayName() + ": Huomenna kello 6", sender.sentTo(aDevice).get(0).body());
        assertEquals(1, sender.sentTo(bDevice).size());
        assertEquals(0, sender.sentTo(myDevice).size());
    }

    @Test
    void message_toUserWithoutDevices_sendsNothing_andDoesNotFail() {
        AppUser me = newUser("Lähettäjä");
        AppUser friend = newUser("Kaveri");
        Conversation c = chats.openDirect(me, friend);

        chats.send(c, me, "Kukaan ei kuuntele");

        assertEquals(1, chats.messages(c, me).size(), "Viesti tallentuu silti");
    }

    @Test
    void longMessage_isShortenedInTheNotification() {
        AppUser me = newUser("Lähettäjä");
        AppUser friend = newUser("Kaveri");
        String device = subscribeDevice(friend);
        Conversation c = chats.openDirect(me, friend);

        chats.send(c, me, "x".repeat(500));

        String body = sender.sentTo(device).get(0).body();
        assertTrue(body.length() <= 81, "Esikatselu on lyhennetty: " + body.length());
        assertTrue(body.endsWith("…"));
    }

    @Test
    void blankMessage_andEditedMessage_sendNoNotification() {
        AppUser me = newUser("Lähettäjä");
        AppUser friend = newUser("Kaveri");
        String device = subscribeDevice(friend);
        Conversation c = chats.openDirect(me, friend);

        chats.send(c, me, "   ");
        assertEquals(0, sender.sentTo(device).size(), "Tyhjää viestiä ei lähetetä");

        chats.send(c, me, "alkuperäinen");
        assertEquals(1, sender.sentTo(device).size());
        var message = chats.messages(c, me).get(0);
        chats.editMessage(message.getId(), me, "korjattu");
        assertEquals(1, sender.sentTo(device).size(), "Muokkaus ei lähetä uutta ilmoitusta");
    }

    @Test
    void removedGroupMember_noLongerGetsNotifications() {
        AppUser me = newUser("Lähettäjä");
        AppUser removed = newUser("Poistettu");
        String device = subscribeDevice(removed);
        Conversation g = chats.createGroup(me, "Ryhmä", Set.of(removed));
        chats.updateGroup(g.getId(), me, "Ryhmä", Set.of());

        chats.send(g, me, "salainen");

        assertEquals(0, sender.sentTo(device).size(), "Ryhmästä poistettu ei saa ilmoituksia");
    }

    // ---- julkaisuilmoitukset ----

    @Test
    void newPost_notifiesFollowersOnly() {
        AppUser author = newUser("Kalastaja");
        AppUser follower = newUser("Seuraaja");
        AppUser stranger = newUser("Tuntematon");
        users.toggleFollow(follower, author);
        users.toggleFollow(stranger, newUser("Joku-muu")); // seuraa jotakuta muuta, mutta ei tekijää
        String followerDevice = subscribeDevice(follower);
        String strangerDevice = subscribeDevice(stranger);
        String authorDevice = subscribeDevice(author);

        posts.create(author, "Hauki", null, null, null, null, null, null, null);

        assertEquals(1, sender.sentTo(followerDevice).size(), "Seuraaja saa ilmoituksen");
        assertEquals("Uusi saalis", sender.sentTo(followerDevice).get(0).title());
        assertEquals(author.getDisplayName() + ": Hauki", sender.sentTo(followerDevice).get(0).body());
        assertEquals(0, sender.sentTo(strangerDevice).size(), "Ei-seuraaja ei saa ilmoitusta");
        assertEquals(0, sender.sentTo(authorDevice).size(), "Tekijä ei saa ilmoitusta omasta julkaisustaan");
    }

    @Test
    void unfollowing_stopsPostNotifications() {
        AppUser author = newUser("Kalastaja");
        AppUser follower = newUser("Seuraaja");
        users.toggleFollow(follower, author);
        String device = subscribeDevice(follower);
        users.toggleFollow(follower, author); // lopeta seuraaminen

        posts.create(author, "Kuha", null, null, null, null, null, null, null);

        assertEquals(0, sender.sentTo(device).size());
    }

    @Test
    void editingOrDeletingAPost_sendsNoNotification() {
        AppUser author = newUser("Kalastaja");
        AppUser follower = newUser("Seuraaja");
        users.toggleFollow(follower, author);
        String device = subscribeDevice(follower);
        CatchPost post = posts.create(author, "Hauki", null, null, null, null, null, null, null);
        assertEquals(1, sender.sentTo(device).size());

        posts.update(post.getId(), author, "Kuha", null, null, null, null, null, null, null, false, null, null);
        posts.delete(post.getId(), author);

        assertEquals(1, sender.sentTo(device).size(), "Vain uusi julkaisu ilmoitetaan");
    }

    // ---- poistuneet tilaukset ----

    @Test
    void goneSubscription_isRemoved_butFailedOneIsKept() {
        AppUser me = newUser("Lähettäjä");
        AppUser friend = newUser("Kaveri");
        String gone = subscribeDevice(friend);
        String flaky = subscribeDevice(friend);
        String healthy = subscribeDevice(friend);
        sender.respondWith(gone, PushSender.Result.GONE);
        sender.respondWith(flaky, PushSender.Result.FAILED);
        Conversation c = chats.openDirect(me, friend);

        chats.send(c, me, "testi");

        assertFalse(push.isSubscribed(friend, gone), "Peruutettu tilaus siivotaan pois");
        assertTrue(push.isSubscribed(friend, flaky), "Tilapäinen virhe ei poista tilausta");
        assertTrue(push.isSubscribed(friend, healthy));
        assertEquals(2, push.deviceCount(friend));
    }

    // ---- apumetodit ----

    @Test
    void goneDetection_recognizesExpiredSubscriptions() {
        assertTrue(VapidPushSender.isGone(new WebPushException("Push service returned 410 Gone")));
        assertTrue(VapidPushSender.isGone(new WebPushException("ei löytynyt", new RuntimeException("HTTP 404"))));
        assertFalse(VapidPushSender.isGone(new WebPushException("Push service returned 500")));
        assertFalse(VapidPushSender.isGone(new WebPushException()));
    }

    @Test
    void preview_collapsesWhitespace_andHandlesNull() {
        assertEquals("a b c", PushNotifier.preview("  a \n\n b\t c  "));
        assertEquals("", PushNotifier.preview(null));
        assertEquals(80, PushNotifier.preview("y".repeat(80)).length());
        assertEquals(81, PushNotifier.preview("y".repeat(81)).length());
    }
}
