package com.narraus.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.narraus.NarrausTest;
import com.narraus.domain.NotificationService.Entry;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Kun ylläpitäjä poistaa tai muokkaa toisen julkaisua, tekijä saa ilmoituksen ja voi valittaa tai antaa palautetta. */
@NarrausTest
class ModerationServiceTest {

    @Autowired
    private ModerationService moderation;
    @Autowired
    private PostService posts;
    @Autowired
    private UserService users;
    @Autowired
    private NotificationService notifications;
    @Autowired
    private AccountService accounts;

    private AppUser admin() {
        return users.find("robbe").orElseGet(() -> users.register("robbe", "Robbe Ylläpitäjä", "salasana123"));
    }

    private AppUser newUser(String prefix) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        return users.register("u-" + id, prefix + "-" + id, "salasana123");
    }

    private CatchPost post(AppUser author) {
        return posts.create(author, "Hauki", 3.0, 80.0, null, "Päijänne", "Alkuperäinen kuvaus", null, null);
    }

    private Entry latest(AppUser u) {
        return notifications.recent(u).get(0);
    }

    // ---- päätöksen kirjaus ja ilmoitus ----

    @Test
    void adminDeletingSomeonesPost_recordsTheDecision_andNotifiesTheAuthorWithTheReason() {
        AppUser owner = newUser("Omistaja");
        CatchPost p = post(owner);

        posts.delete(p.getId(), admin(), "Ei kalakuva");

        Entry e = latest(owner);
        assertEquals(NotificationType.MODERATION_DELETED, e.kind());
        assertEquals("Hauki", e.label());
        ModerationAction action = moderation.actionFor(e.targetId(), owner).orElseThrow();
        assertEquals(ModerationKind.DELETED, action.getKind());
        assertEquals("Ei kalakuva", action.getReason());
        assertEquals("Alkuperäinen kuvaus", action.getDescription(), "Poistetun julkaisun sisältö jää muistiin valitusta varten");
        assertTrue(posts.byAuthor(owner).isEmpty());
    }

    @Test
    void adminEditingSomeonesPost_recordsTheOriginalContent_andNotifiesTheAuthor() {
        AppUser owner = newUser("Omistaja");
        CatchPost p = post(owner);

        posts.update(p.getId(), admin(), "Kuha", 2.0, 60.0, null, "Lahti", "Siivottu", null, null, false, null, null);

        Entry e = latest(owner);
        assertEquals(NotificationType.MODERATION_EDITED, e.kind());
        ModerationAction action = moderation.actionFor(e.targetId(), owner).orElseThrow();
        assertEquals(ModerationKind.EDITED, action.getKind());
        assertEquals("Hauki", action.getSpecies(), "Muistiin jää alkuperäinen laji");
        assertEquals("Alkuperäinen kuvaus", action.getDescription());
    }

    @Test
    void authorsOwnChanges_createNoDecision() {
        AppUser owner = newUser("Omistaja");
        CatchPost p = post(owner);

        posts.update(p.getId(), owner, "Kuha", null, null, null, null, null, null, null, false, null, null);
        posts.delete(p.getId(), owner);

        assertTrue(notifications.recent(owner).stream().noneMatch(e -> e.kind() == NotificationType.MODERATION_DELETED
                || e.kind() == NotificationType.MODERATION_EDITED));
    }

    @Test
    void decision_isVisibleOnlyToTheAuthorAndTheAdmin() {
        AppUser owner = newUser("Omistaja");
        AppUser stranger = newUser("Vieras");
        posts.delete(post(owner).getId(), admin(), null);
        Long id = latest(owner).targetId();

        assertTrue(moderation.actionFor(id, owner).isPresent());
        assertTrue(moderation.actionFor(id, admin()).isPresent());
        assertTrue(moderation.actionFor(id, stranger).isEmpty());
        assertTrue(moderation.actionFor(null, owner).isEmpty());
    }

    // ---- valitus ja palaute ----

    @Test
    void author_canComplain_andTheAdminIsNotifiedAndSeesIt() {
        AppUser owner = newUser("Omistaja");
        AppUser admin = admin();
        posts.delete(post(owner).getId(), admin, "Syy");
        Long id = latest(owner).targetId();

        ModerationAppeal appeal = moderation.appeal(id, owner, "  Kalani oli oikea, tässä todiste.  ");

        assertEquals("Kalani oli oikea, tässä todiste.", appeal.getText());
        assertEquals(1, moderation.appealsOf(moderation.actionFor(id, owner).orElseThrow()).size());
        Entry toAdmin = latest(admin);
        assertEquals(NotificationType.APPEAL, toAdmin.kind());
        assertEquals(owner.getId(), toAdmin.actor().getId());
        assertTrue(moderation.allAppeals(admin).stream().anyMatch(a -> a.getId().equals(appeal.getId())));
    }

    @Test
    void onlyTheAuthor_mayAppeal_andTextIsValidated() {
        AppUser owner = newUser("Omistaja");
        AppUser stranger = newUser("Vieras");
        posts.delete(post(owner).getId(), admin(), null);
        Long id = latest(owner).targetId();

        assertThrows(SecurityException.class, () -> moderation.appeal(id, stranger, "Huijaus"));
        assertThrows(SecurityException.class, () -> moderation.appeal(id, null, "Huijaus"));
        assertThrows(IllegalArgumentException.class, () -> moderation.appeal(id, owner, "   "));
        assertThrows(IllegalArgumentException.class, () -> moderation.appeal(id, owner, null));
        assertThrows(IllegalArgumentException.class,
                () -> moderation.appeal(id, owner, "x".repeat(ModerationService.MAX_APPEAL_LENGTH + 1)));
        assertThrows(IllegalArgumentException.class, () -> moderation.appeal(999_999L, owner, "Viesti"));
        moderation.appeal(id, owner, "x".repeat(ModerationService.MAX_APPEAL_LENGTH)); // raja on sallittu
    }

    @Test
    void appealsPerDecision_areLimited() {
        AppUser owner = newUser("Omistaja");
        posts.delete(post(owner).getId(), admin(), null);
        Long id = latest(owner).targetId();
        for (int i = 0; i < ModerationService.MAX_APPEALS_PER_ACTION; i++) moderation.appeal(id, owner, "Viesti " + i);

        assertThrows(IllegalArgumentException.class, () -> moderation.appeal(id, owner, "Liikaa"));
    }

    @Test
    void allAppeals_areForTheAdminOnly() {
        AppUser normal = newUser("Tavallinen");

        assertThrows(SecurityException.class, () -> moderation.allAppeals(normal));
        assertThrows(SecurityException.class, () -> moderation.allAppeals(null));
        assertNotNull(moderation.allAppeals(admin()));
    }

    @Test
    void deletingAnAccount_removesItsDecisionsAndAppeals() {
        AppUser owner = newUser("Omistaja");
        posts.delete(post(owner).getId(), admin(), "Syy");
        Long id = latest(owner).targetId();
        moderation.appeal(id, owner, "Valitus");

        accounts.deleteAccount(owner.getId(), "salasana123"); // ei saa kaatua viiteavaimeen

        assertTrue(users.findById(owner.getId()).isEmpty());
        assertFalse(moderation.allAppeals(admin()).stream().anyMatch(a -> a.getAction().getId().equals(id)));
    }
}
