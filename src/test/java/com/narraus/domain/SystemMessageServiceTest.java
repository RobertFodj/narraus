package com.narraus.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.narraus.NarrausTest;
import com.narraus.RecordingPushSender;
import com.vaadin.flow.server.webpush.WebPushKeys;
import com.vaadin.flow.server.webpush.WebPushSubscription;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Narraus-viestit: vain ylläpitäjä lähettää; vastaanottaja näkee lähettäjäksi "Narraus" ja saa ilmoituksen. */
@NarrausTest
class SystemMessageServiceTest {

    @Autowired
    private SystemMessageService system;
    @Autowired
    private UserService users;
    @Autowired
    private ChatService chats;
    @Autowired
    private UnreadService unread;
    @Autowired
    private PushService push;
    @Autowired
    private RecordingPushSender sender;
    @Autowired
    private AccountService accounts;

    private AppUser admin() {
        return users.find("robbe").orElseGet(() -> users.register("robbe", "Robbe Ylläpitäjä", "salasana123"));
    }

    private AppUser newUser(String prefix) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        return users.register("u-" + id, prefix + "-" + id, "salasana123");
    }

    private String subscribeDevice(AppUser user) {
        String endpoint = "https://push.example.test/" + UUID.randomUUID();
        push.subscribe(user, new WebPushSubscription(endpoint, new WebPushKeys("k", "a")));
        return endpoint;
    }

    // ---- vain ylläpitäjä ----

    @Test
    void onlyTheAdmin_maySend() {
        AppUser stranger = newUser("Tavallinen");
        AppUser target = newUser("Kohde");

        assertThrows(SecurityException.class, () -> system.send(stranger, Set.of(target), "Huijaus"));
        assertThrows(SecurityException.class, () -> system.sendToAll(stranger, "Huijaus"));
        assertThrows(SecurityException.class, () -> system.send(null, Set.of(target), "Huijaus"));
        assertThrows(SecurityException.class, () -> system.sendToAll(null, "Huijaus"));
        assertTrue(system.messagesFor(target).isEmpty(), "Kiellettyjä viestejä ei tallennu");
    }

    // ---- vastaanottajat ----

    @Test
    void send_reachesOnlyTheChosenUsers() {
        AppUser a = newUser("A");
        AppUser b = newUser("B");
        AppUser other = newUser("Muu");

        int count = system.send(admin(), Set.of(a, b), "Huoltokatko huomenna");

        assertEquals(2, count);
        assertEquals(List.of("Huoltokatko huomenna"), system.messagesFor(a).stream().map(SystemMessage::getText).toList());
        assertEquals(1, system.messagesFor(b).size());
        assertTrue(system.messagesFor(other).isEmpty(), "Valitsematta jäänyt ei saa viestiä");
        assertTrue(system.messagesFor(admin()).stream().noneMatch(m -> m.getText().equals("Huoltokatko huomenna")),
                "Lähettäjä ei saa viestiä itselleen, ellei valitse itseään");
    }

    @Test
    void sendToAll_reachesEveryoneExceptTheSender() {
        AppUser a = newUser("A");
        AppUser b = newUser("B");
        AppUser admin = admin();
        String text = "Kaikille-" + UUID.randomUUID();

        int count = system.sendToAll(admin, text);

        assertEquals(users.countOthers("", admin), count, "Viesti menee kaikille muille");
        assertTrue(system.messagesFor(a).stream().anyMatch(m -> m.getText().equals(text)));
        assertTrue(system.messagesFor(b).stream().anyMatch(m -> m.getText().equals(text)));
        assertTrue(system.messagesFor(admin).stream().noneMatch(m -> m.getText().equals(text)),
                "Ylläpitäjä ei saa kopiota omasta joukkoviestistään");
    }

    @Test
    void messagesAreStoredPerRecipient_andOrderedOldestFirst() {
        AppUser a = newUser("A");
        AppUser b = newUser("B");

        system.send(admin(), Set.of(a), "ensimmäinen");
        system.send(admin(), Set.of(a, b), "toinen");
        system.send(admin(), Set.of(a), "kolmas");

        assertEquals(List.of("ensimmäinen", "toinen", "kolmas"),
                system.messagesFor(a).stream().map(SystemMessage::getText).toList());
        assertEquals(List.of("toinen"), system.messagesFor(b).stream().map(SystemMessage::getText).toList());
    }

    // ---- sisältö ----

    @Test
    void text_isTrimmed_andValidated() {
        AppUser a = newUser("A");

        system.send(admin(), Set.of(a), "   siistitty viesti  \n");
        assertEquals("siistitty viesti", system.messagesFor(a).get(0).getText());

        assertThrows(IllegalArgumentException.class, () -> system.send(admin(), Set.of(a), "   "));
        assertThrows(IllegalArgumentException.class, () -> system.send(admin(), Set.of(a), null));
        assertThrows(IllegalArgumentException.class, () -> system.sendToAll(admin(), ""));
        assertThrows(IllegalArgumentException.class,
                () -> system.send(admin(), Set.of(a), "x".repeat(SystemMessageService.MAX_LENGTH + 1)));
        system.send(admin(), Set.of(a), "x".repeat(SystemMessageService.MAX_LENGTH)); // raja on sallittu
        assertEquals(2, system.messagesFor(a).size(), "Virheelliset viestit eivät tallennu");
    }

    @Test
    void sendWithoutRecipients_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> system.send(admin(), Set.of(), "Ei kenellekään"));
        assertThrows(IllegalArgumentException.class, () -> system.send(admin(), null, "Ei kenellekään"));
    }

    @Test
    void theSenderIsAlwaysNarraus_andTheAdminIsNeverExposed() {
        assertEquals("Narraus", SystemMessageService.SENDER_NAME);
        boolean exposesSender = Arrays.stream(SystemMessage.class.getMethods())
                .anyMatch(m -> m.getName().toLowerCase().contains("sentby") || m.getName().toLowerCase().contains("sender"));
        assertFalse(exposesSender, "Viestiolio ei saa paljastaa lähettävää ylläpitäjää vastaanottajalle");
    }

    // ---- lukemattomat ----

    @Test
    void unreadCount_andMarkAllRead() {
        AppUser a = newUser("A");
        AppUser b = newUser("B");
        system.send(admin(), Set.of(a), "yksi");
        system.send(admin(), Set.of(a, b), "kaksi");

        assertEquals(2, system.unreadCount(a));
        assertEquals(1, system.unreadCount(b));
        assertTrue(system.hasMessages(a));

        assertTrue(system.markAllRead(a), "Lukemattomia oli");
        assertEquals(0, system.unreadCount(a));
        assertFalse(system.markAllRead(a), "Toisella kerralla ei enää lukemattomia");
        assertEquals(1, system.unreadCount(b), "Toisen lukemattomat eivät muutu");
        assertTrue(system.messagesFor(a).stream().allMatch(SystemMessage::isRead));
        assertTrue(system.hasMessages(a), "Luetut viestit säilyvät");
    }

    @Test
    void userWithoutSystemMessages_hasNone() {
        AppUser a = newUser("A");

        assertFalse(system.hasMessages(a));
        assertEquals(0, system.unreadCount(a));
        assertFalse(system.markAllRead(a));
    }

    @Test
    void systemMessages_countTowardsTheUnreadBadge_alongsideChatMessages() {
        AppUser me = newUser("Minä");
        AppUser friend = newUser("Kaveri");
        Conversation c = chats.openDirect(me, friend);
        chats.send(c, friend, "tavallinen viesti");
        system.send(admin(), Set.of(me), "Narraus-viesti");

        assertEquals(2, unread.unreadMessages(me), "Tavallinen viesti ja Narraus-viesti molemmat lasketaan");

        system.markAllRead(me);
        assertEquals(1, unread.unreadMessages(me), "Narraus-viestin lukeminen ei merkitse keskustelua luetuksi");
    }

    // ---- push-ilmoitus ----

    @Test
    void recipients_getAPushNotification_fromNarrausWithTheLogo() {
        AppUser a = newUser("A");
        AppUser outsider = newUser("Ulkopuolinen");
        String device = subscribeDevice(a);
        String outsiderDevice = subscribeDevice(outsider);

        system.send(admin(), Set.of(a), "Sovellus päivittyy illalla");

        assertEquals(1, sender.sentTo(device).size());
        var n = sender.sentTo(device).get(0);
        assertEquals("Narraus", n.title(), "Ilmoituksen lähettäjä on Narraus");
        assertEquals("Sovellus päivittyy illalla", n.body());
        assertEquals("/icons/icon-192x192.png", n.icon(), "Kuvakkeena Narrauksen logo");
        assertEquals(0, sender.sentTo(outsiderDevice).size(), "Valitsematon ei saa ilmoitusta");
    }

    @Test
    void notification_reachesAllDevicesOfTheRecipient_andLongTextsAreShortened() {
        AppUser a = newUser("A");
        String phone = subscribeDevice(a);
        String laptop = subscribeDevice(a);

        system.send(admin(), Set.of(a), "y".repeat(400));

        assertEquals(1, sender.sentTo(phone).size());
        assertEquals(1, sender.sentTo(laptop).size());
        String body = sender.sentTo(phone).get(0).body();
        assertTrue(body.length() <= 81 && body.endsWith("…"), "Ilmoituksessa vain lyhyt esikatselu");
        assertEquals(400, system.messagesFor(a).get(0).getText().length(), "Koko viesti säilyy sovelluksessa");
    }

    @Test
    void ordinaryNotifications_keepTheirOwnSenderAndNoLogo() {
        AppUser me = newUser("Minä");
        AppUser friend = newUser("Kaveri");
        String device = subscribeDevice(friend);
        Conversation c = chats.openDirect(me, friend);

        chats.send(c, me, "tavallinen");

        var n = sender.sentTo(device).get(0);
        assertEquals(me.getDisplayName(), n.title(), "Tavallisen viestin lähettäjä ei muutu Narrausksi");
        assertNull(n.icon());
    }

    @Test
    void rejectedMessage_sendsNoNotification() {
        AppUser a = newUser("A");
        String device = subscribeDevice(a);
        AppUser stranger = newUser("Tavallinen");

        assertThrows(SecurityException.class, () -> system.send(stranger, Set.of(a), "Huijaus"));
        assertThrows(IllegalArgumentException.class, () -> system.send(admin(), Set.of(a), " "));

        assertEquals(0, sender.sentTo(device).size());
    }

    // ---- tilin poisto ----

    @Test
    void deletingTheAccount_removesTheUsersSystemMessages() {
        AppUser a = newUser("A");
        system.send(admin(), Set.of(a), "poistuva");
        assertEquals(1, system.messagesFor(a).size());

        accounts.deleteAccount(a.getId(), "salasana123"); // ei saa kaatua viiteavaimeen

        assertTrue(users.findById(a.getId()).isEmpty());
    }
}
