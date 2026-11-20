package com.narraus.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.narraus.NarrausTest;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Viestien ja ryhmien muokkaus palvelukerroksessa. */
@NarrausTest
class ChatEditTest {

    @Autowired
    private ChatService chats;
    @Autowired
    private UserService users;

    private AppUser newUser(String prefix) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        return users.register("u-" + id, prefix + "-" + id, "salasana123");
    }

    private ChatMessage lastMessage(Conversation c, AppUser viewer) {
        List<ChatMessage> all = chats.messages(c, viewer);
        return all.get(all.size() - 1);
    }

    // ---- viestin muokkaus ----

    @Test
    void editMessage_bySender_updatesTextAndMarksItEdited() {
        AppUser me = newUser("Minä");
        AppUser friend = newUser("Kaveri");
        Conversation c = chats.openDirect(me, friend);
        chats.send(c, me, "Alkuperäinen");
        ChatMessage m = lastMessage(c, me);
        assertFalse(m.isEdited());

        chats.editMessage(m.getId(), me, "  Korjattu viesti  ");

        ChatMessage edited = lastMessage(c, friend);
        assertEquals("Korjattu viesti", edited.getText(), "Teksti päivittyy ja reunat siistitään");
        assertTrue(edited.isEdited(), "Muokattu viesti merkitään muokatuksi");
        assertNotNull(edited.getEditedAt());
        assertEquals(m.getSentAt(), edited.getSentAt(), "Lähetysaika ei muutu");
        assertEquals(me, edited.getSender());
    }

    @Test
    void editMessage_withSameText_isNotMarkedEdited() {
        AppUser me = newUser("Minä");
        AppUser friend = newUser("Kaveri");
        Conversation c = chats.openDirect(me, friend);
        chats.send(c, me, "Sama");
        ChatMessage m = lastMessage(c, me);

        chats.editMessage(m.getId(), me, "Sama");

        assertFalse(lastMessage(c, me).isEdited());
    }

    @Test
    void editMessage_byOtherMember_isForbidden_andChangesNothing() {
        AppUser me = newUser("Minä");
        AppUser friend = newUser("Kaveri");
        Conversation c = chats.openDirect(me, friend);
        chats.send(c, me, "Minun viestini");
        ChatMessage m = lastMessage(c, me);

        assertThrows(SecurityException.class, () -> chats.editMessage(m.getId(), friend, "Väärennetty"));

        assertEquals("Minun viestini", lastMessage(c, me).getText());
        assertFalse(lastMessage(c, me).isEdited());
    }

    @Test
    void editMessage_byOutsider_isForbidden() {
        AppUser me = newUser("Minä");
        AppUser friend = newUser("Kaveri");
        AppUser outsider = newUser("Ulkopuolinen");
        Conversation c = chats.openDirect(me, friend);
        chats.send(c, me, "Yksityinen");
        ChatMessage m = lastMessage(c, me);

        assertThrows(SecurityException.class, () -> chats.editMessage(m.getId(), outsider, "Hakattu"));
        assertThrows(SecurityException.class, () -> chats.editMessage(m.getId(), null, "Hakattu"));
    }

    @Test
    void editMessage_blankTooLongOrUnknown_isRejected() {
        AppUser me = newUser("Minä");
        AppUser friend = newUser("Kaveri");
        Conversation c = chats.openDirect(me, friend);
        chats.send(c, me, "Viesti");
        ChatMessage m = lastMessage(c, me);

        assertThrows(IllegalArgumentException.class, () -> chats.editMessage(m.getId(), me, "   "));
        assertThrows(IllegalArgumentException.class, () -> chats.editMessage(m.getId(), me, null));
        assertThrows(IllegalArgumentException.class, () -> chats.editMessage(m.getId(), me, "x".repeat(2001)));
        assertThrows(IllegalArgumentException.class, () -> chats.editMessage(987654321L, me, "Teksti"));
        assertEquals("Viesti", lastMessage(c, me).getText());
        chats.editMessage(m.getId(), me, "x".repeat(2000)); // raja on 2000 merkkiä, ei 1999
        assertEquals(2000, lastMessage(c, me).getText().length());
    }

    @Test
    void myRecentMessages_returnsOnlyOwn_newestFirst_atMostTwenty() throws Exception {
        AppUser me = newUser("Minä");
        AppUser friend = newUser("Kaveri");
        Conversation c = chats.openDirect(me, friend);
        for (int i = 1; i <= 25; i++) {
            chats.send(c, me, "oma " + i);
            Thread.sleep(2);
        }
        chats.send(c, friend, "kaverin viesti");

        List<ChatMessage> mine = chats.myRecentMessages(c, me);

        assertEquals(20, mine.size());
        assertEquals("oma 25", mine.get(0).getText(), "Uusin ensin");
        assertTrue(mine.stream().allMatch(m -> m.getSender().equals(me)), "Vain omia viestejä");
        assertThrows(SecurityException.class, () -> chats.myRecentMessages(c, newUser("Ulkopuolinen")));
    }

    // ---- ryhmän muokkaus ----

    @Test
    void updateGroup_renamesAndChangesMembers() {
        AppUser me = newUser("Minä");
        AppUser a = newUser("A");
        AppUser b = newUser("B");
        AppUser extra = newUser("Uusi");
        Conversation g = chats.createGroup(me, "Vanha nimi", Set.of(a, b));

        chats.updateGroup(g.getId(), me, "  Uusi nimi  ", Set.of(a, extra));

        Conversation updated = chats.get(g.getId(), me);
        assertEquals("Uusi nimi", updated.getName());
        assertEquals(Set.of(me, a, extra), updated.getParticipants());
        assertNull(chats.get(g.getId(), b), "Poistettu jäsen ei enää näe ryhmää");
        assertTrue(chats.conversationsOf(extra).stream().anyMatch(x -> x.getId().equals(g.getId())));
        assertFalse(chats.conversationsOf(b).stream().anyMatch(x -> x.getId().equals(g.getId())));
    }

    @Test
    void updateGroup_requesterAlwaysStaysMember() {
        AppUser me = newUser("Minä");
        AppUser a = newUser("A");
        Conversation g = chats.createGroup(me, "Ryhmä", Set.of(a));

        chats.updateGroup(g.getId(), me, "Ryhmä", Set.of());

        assertEquals(Set.of(me), chats.get(g.getId(), me).getParticipants());
    }

    @Test
    void updateGroup_byNonMember_isForbidden_andChangesNothing() {
        AppUser me = newUser("Minä");
        AppUser a = newUser("A");
        AppUser outsider = newUser("Ulkopuolinen");
        Conversation g = chats.createGroup(me, "Ryhmä", Set.of(a));

        assertThrows(SecurityException.class, () -> chats.updateGroup(g.getId(), outsider, "Kaapattu", Set.of()));

        assertEquals("Ryhmä", chats.get(g.getId(), me).getName());
        assertEquals(Set.of(me, a), chats.get(g.getId(), me).getParticipants());
    }

    @Test
    void updateGroup_directConversation_blankName_andUnknown_areRejected() {
        AppUser me = newUser("Minä");
        AppUser a = newUser("A");
        Conversation direct = chats.openDirect(me, a);
        Conversation g = chats.createGroup(me, "Ryhmä", Set.of(a));

        assertThrows(IllegalArgumentException.class, () -> chats.updateGroup(direct.getId(), me, "Nimi", Set.of()));
        assertThrows(IllegalArgumentException.class, () -> chats.updateGroup(g.getId(), me, "   ", Set.of(a)));
        assertThrows(SecurityException.class, () -> chats.updateGroup(987654321L, me, "Nimi", Set.of()));
        assertEquals("Ryhmä", chats.get(g.getId(), me).getName());
    }

    @Test
    void removedMember_cannotReadOrSend_evenWithStaleConversation() {
        AppUser me = newUser("Minä");
        AppUser removed = newUser("Poistettava");
        Conversation g = chats.createGroup(me, "Ryhmä", Set.of(removed));
        Conversation staleForRemoved = chats.get(g.getId(), removed); // vanhentuva kopio avoimessa näkymässä
        chats.send(g, me, "ennen poistoa");

        chats.updateGroup(g.getId(), me, "Ryhmä", Set.of());

        assertThrows(SecurityException.class, () -> chats.messages(staleForRemoved, removed),
                "Poistettu jäsen ei saa lukea vanhentuneella kopiolla");
        assertThrows(SecurityException.class, () -> chats.send(staleForRemoved, removed, "yritys"));
        assertFalse(chats.isMember(staleForRemoved, removed));
    }

    @Test
    void sendWithStaleConversation_doesNotBringRemovedMemberBack() {
        AppUser me = newUser("Minä");
        AppUser other = newUser("Toinen");
        AppUser removed = newUser("Poistettava");
        Conversation g = chats.createGroup(me, "Ryhmä", Set.of(other, removed));
        Conversation staleForOther = chats.get(g.getId(), other); // sisältää vielä poistettavan

        chats.updateGroup(g.getId(), me, "Ryhmä", Set.of(other));
        chats.send(staleForOther, other, "viesti vanhentuneelta kopiolta");

        assertNull(chats.get(g.getId(), removed), "Vanhentunut kopio ei saa palauttaa poistettua jäsentä");
        assertEquals(Set.of(me, other), chats.get(g.getId(), me).getParticipants());
    }

    @Test
    void messagesStayAfterGroupEdit() {
        AppUser me = newUser("Minä");
        AppUser a = newUser("A");
        Conversation g = chats.createGroup(me, "Ryhmä", Set.of(a));
        chats.send(g, me, "ensimmäinen");
        chats.send(g, a, "toinen");

        chats.updateGroup(g.getId(), me, "Uusi nimi", Set.of(a));

        assertEquals(2, chats.messages(g, me).size());
    }
}
