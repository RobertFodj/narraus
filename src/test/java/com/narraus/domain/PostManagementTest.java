package com.narraus.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.narraus.NarrausTest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Oman julkaisun muokkaus ja poisto palvelukerroksessa. Vain tekijä saa muokata ja poistaa. */
@NarrausTest
class PostManagementTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3, 4};

    @Autowired
    private PostService posts;
    @Autowired
    private UserService users;

    private AppUser newUser(String prefix) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        return users.register("u-" + id, prefix + "-" + id, "salasana123");
    }

    private CatchPost newPost(AppUser author) {
        return posts.create(author, "Hauki", 3.5, 80.0, 40.0, "Päijänne", "Alkuperäinen kuvaus", null, null,
                61.5, 25.5);
    }

    private CatchPost reload(AppUser author, Long id) {
        return posts.byAuthor(author).stream().filter(p -> p.getId().equals(id)).findFirst().orElse(null);
    }

    private boolean photoExists(String file) {
        try (var in = posts.openPhoto(file)) {
            return in != null;
        } catch (UncheckedIOException | IOException e) {
            return false;
        }
    }

    // ---- muokkaus ----

    @Test
    void update_byOwner_changesFields_andKeepsAuthorAndCreationTime() {
        AppUser me = newUser("Minä");
        CatchPost post = newPost(me);
        var createdBefore = reload(me, post.getId()).getCreatedAt(); // tietokannasta ladattu (mikrosekuntitarkkuus)

        posts.update(post.getId(), me, "  Kuha  ", 2.0, 60.0, null, " Näsijärvi ", "  Uusi kuvaus ", null, null,
                false, 61.5, 25.5);

        CatchPost saved = reload(me, post.getId());
        assertEquals("Kuha", saved.getSpecies(), "Lajista karsitaan tyhjät");
        assertEquals(2.0, saved.getWeightKg());
        assertEquals(60.0, saved.getLengthCm());
        assertNull(saved.getGirthCm(), "Tyhjäksi jätetty arvo tyhjenee");
        assertEquals("Näsijärvi", saved.getLocation());
        assertEquals("Uusi kuvaus", saved.getDescription());
        assertEquals(me, saved.getAuthor());
        assertEquals(createdBefore, saved.getCreatedAt(), "Luontiaika ei muutu muokatessa");
    }

    @Test
    void update_canMoveClearAndSetCoordinates() {
        AppUser me = newUser("Minä");
        CatchPost post = newPost(me);

        posts.update(post.getId(), me, "Hauki", null, null, null, null, null, null, null, false, 62.0, 26.0);
        assertEquals(62.0, reload(me, post.getId()).getLatitude());
        assertEquals(26.0, reload(me, post.getId()).getLongitude());

        posts.update(post.getId(), me, "Hauki", null, null, null, null, null, null, null, false, null, null);
        assertNull(reload(me, post.getId()).getLatitude());
        assertNull(reload(me, post.getId()).getLongitude());
        assertFalse(posts.withLocation().stream().anyMatch(p -> p.getId().equals(post.getId())),
                "Sijaintia vailla oleva julkaisu poistuu kartalta");
    }

    @Test
    void update_byOtherUser_isForbidden_andChangesNothing() {
        AppUser owner = newUser("Omistaja");
        AppUser intruder = newUser("Tunkeilija");
        CatchPost post = newPost(owner);

        assertThrows(SecurityException.class, () -> posts.update(post.getId(), intruder, "Hakattu", 99.0, null,
                null, null, "Vandalismi", null, null, false, null, null));

        CatchPost unchanged = reload(owner, post.getId());
        assertEquals("Hauki", unchanged.getSpecies());
        assertEquals(3.5, unchanged.getWeightKg());
        assertEquals("Alkuperäinen kuvaus", unchanged.getDescription());
        assertEquals(61.5, unchanged.getLatitude());
    }

    @Test
    void update_withoutRequester_isForbidden() {
        AppUser owner = newUser("Omistaja");
        CatchPost post = newPost(owner);

        assertThrows(SecurityException.class, () -> posts.update(post.getId(), null, "Kuha", null, null, null,
                null, null, null, null, false, null, null));
    }

    @Test
    void update_unknownPost_isRejected() {
        AppUser me = newUser("Minä");
        assertThrows(IllegalArgumentException.class, () -> posts.update(987654321L, me, "Kuha", null, null, null,
                null, null, null, null, false, null, null));
    }

    @Test
    void update_blankSpecies_isRejected_andChangesNothing() {
        AppUser me = newUser("Minä");
        CatchPost post = newPost(me);

        assertThrows(IllegalArgumentException.class, () -> posts.update(post.getId(), me, "   ", null, null, null,
                null, null, null, null, false, null, null));
        assertThrows(IllegalArgumentException.class, () -> posts.update(post.getId(), me, null, null, null, null,
                null, null, null, null, false, null, null));
        assertEquals("Hauki", reload(me, post.getId()).getSpecies());
    }

    @Test
    void update_invalidCoordinates_areRejected() {
        AppUser me = newUser("Minä");
        CatchPost post = newPost(me);

        assertThrows(IllegalArgumentException.class, () -> posts.update(post.getId(), me, "Hauki", null, null, null,
                null, null, null, null, false, 95.0, 25.0));
        assertEquals(61.5, reload(me, post.getId()).getLatitude());
    }

    @Test
    void update_keepsPhoto_whenNoNewPhotoGiven() {
        AppUser me = newUser("Minä");
        CatchPost post = posts.create(me, "Hauki", null, null, null, null, null, PNG, "image/png");
        String file = post.getPhotoFile();
        assertTrue(photoExists(file));

        posts.update(post.getId(), me, "Kuha", null, null, null, null, null, null, null, false, null, null);

        assertEquals(file, reload(me, post.getId()).getPhotoFile());
        assertTrue(photoExists(file), "Kuva säilyy, kun uutta ei anneta");
    }

    @Test
    void update_newPhoto_replacesOldAndDeletesOldFile() {
        AppUser me = newUser("Minä");
        CatchPost post = posts.create(me, "Hauki", null, null, null, null, null, PNG, "image/png");
        String oldFile = post.getPhotoFile();

        posts.update(post.getId(), me, "Hauki", null, null, null, null, null, PNG, "image/png", false, null, null);

        String newFile = reload(me, post.getId()).getPhotoFile();
        assertNotEquals(oldFile, newFile);
        assertTrue(photoExists(newFile));
        assertFalse(photoExists(oldFile), "Korvattu kuva poistetaan levyltä");
    }

    @Test
    void update_removePhoto_clearsItAndDeletesFile() {
        AppUser me = newUser("Minä");
        CatchPost post = posts.create(me, "Hauki", null, null, null, null, null, PNG, "image/png");
        String file = post.getPhotoFile();

        posts.update(post.getId(), me, "Hauki", null, null, null, null, null, null, null, true, null, null);

        assertNull(reload(me, post.getId()).getPhotoFile());
        assertFalse(photoExists(file));
    }

    @Test
    void update_withBadPhotoType_isRejected_andKeepsOldPhoto() {
        AppUser me = newUser("Minä");
        CatchPost post = posts.create(me, "Hauki", null, null, null, null, null, PNG, "image/png");
        String file = post.getPhotoFile();

        assertThrows(IllegalArgumentException.class, () -> posts.update(post.getId(), me, "Hauki", null, null, null,
                null, null, PNG, "application/pdf", false, null, null));

        assertEquals(file, reload(me, post.getId()).getPhotoFile());
        assertTrue(photoExists(file));
    }

    // ---- poisto ----

    @Test
    void delete_byOwner_removesPost_likesAndPhoto() {
        AppUser me = newUser("Minä");
        AppUser fan = newUser("Fani");
        CatchPost post = posts.create(me, "Hauki", null, null, null, null, null, PNG, "image/png", 61.5, 25.5);
        String file = post.getPhotoFile();
        posts.toggleLike(post, fan);
        posts.toggleLike(post, me);
        assertEquals(2, posts.likeCount(post));

        posts.delete(post.getId(), me); // ei saa kaatua tykkäysten viiteavaimeen

        assertNull(reload(me, post.getId()));
        assertEquals(0, posts.postCount(me));
        assertFalse(photoExists(file), "Kuva poistetaan levyltä");
        assertFalse(posts.withLocation().stream().anyMatch(p -> p.getId().equals(post.getId())));
        assertFalse(posts.feed(me, false).stream().anyMatch(p -> p.getId().equals(post.getId())));
    }

    @Test
    void delete_byOtherUser_isForbidden_andKeepsEverything() {
        AppUser owner = newUser("Omistaja");
        AppUser intruder = newUser("Tunkeilija");
        CatchPost post = posts.create(owner, "Hauki", null, null, null, null, null, PNG, "image/png");
        String file = post.getPhotoFile();

        assertThrows(SecurityException.class, () -> posts.delete(post.getId(), intruder));

        assertEquals(post.getId(), reload(owner, post.getId()).getId());
        assertTrue(photoExists(file));
    }

    @Test
    void delete_unknownPost_isRejected() {
        AppUser me = newUser("Minä");
        assertThrows(IllegalArgumentException.class, () -> posts.delete(987654321L, me));
    }

    @Test
    void delete_onlyRemovesTheGivenPost() {
        AppUser me = newUser("Minä");
        CatchPost a = newPost(me);
        CatchPost b = newPost(me);

        posts.delete(a.getId(), me);

        assertNull(reload(me, a.getId()));
        assertEquals(b.getId(), reload(me, b.getId()).getId());
        assertEquals(1, posts.postCount(me));
    }

    // ---- ylläpitäjä (robbe) saa muokata ja poistaa kenen tahansa julkaisun ----

    private AppUser admin() {
        return users.find("robbe").orElseGet(() -> users.register("robbe", "Robbe Ylläpitäjä", "salasana123"));
    }

    @Test
    void admin_mayEditAnyonesPost_andTheAuthorStaysTheSame() {
        AppUser owner = newUser("Omistaja");
        CatchPost post = newPost(owner);

        posts.update(post.getId(), admin(), "Kuha", 2.0, 60.0, null, "Moderoitu paikka", "Siivottu kuvaus", null, null,
                false, 61.5, 25.5);

        CatchPost edited = reload(owner, post.getId());
        assertEquals("Kuha", edited.getSpecies());
        assertEquals("Siivottu kuvaus", edited.getDescription());
        assertEquals(owner.getId(), edited.getAuthor().getId(), "Tekijä ei muutu");
    }

    @Test
    void admin_mayDeleteAnyonesPost() {
        AppUser owner = newUser("Omistaja");
        CatchPost post = newPost(owner);

        posts.delete(post.getId(), admin());

        assertEquals(null, reload(owner, post.getId()));
    }

    @Test
    void ordinaryUsers_stillCannotTouchOthersPosts() {
        AppUser owner = newUser("Omistaja");
        AppUser other = newUser("Toinen");
        CatchPost post = newPost(owner);

        assertThrows(SecurityException.class, () -> posts.delete(post.getId(), other));
        assertTrue(reload(owner, post.getId()) != null);
    }
}
