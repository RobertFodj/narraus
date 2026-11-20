package com.narraus.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.narraus.NarrausTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@NarrausTest
class CommentTest {

    private static final String PASSWORD = "salasana123";

    @Autowired
    private UserService users;
    @Autowired
    private PostService posts;
    @Autowired
    private AccountService accounts;

    private AppUser newUser(String prefix) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        return users.register("u-" + id, prefix + "-" + id, PASSWORD);
    }

    @Test
    void anyUser_canComment_andCommentsComeInOrder() {
        AppUser owner = newUser("Omistaja");
        AppUser other = newUser("Toinen");
        CatchPost post = posts.create(owner, "Hauki", null, null, null, null, null, null, null);

        posts.addComment(post.getId(), other, "  Hieno kala!  ");
        posts.addComment(post.getId(), owner, "Kiitos");

        var list = posts.comments(post);
        assertEquals(2, list.size());
        assertEquals("Hieno kala!", list.get(0).getText(), "Teksti siistitään");
        assertEquals(other, list.get(0).getAuthor());
        assertEquals("Kiitos", list.get(1).getText());
        assertEquals(2, posts.commentCount(post));
    }

    @Test
    void emptyOrTooLongComment_isRejected() {
        AppUser me = newUser("Minä");
        CatchPost post = posts.create(me, "Hauki", null, null, null, null, null, null, null);

        assertThrows(IllegalArgumentException.class, () -> posts.addComment(post.getId(), me, "   "));
        assertThrows(IllegalArgumentException.class, () -> posts.addComment(post.getId(), me, null));
        assertThrows(IllegalArgumentException.class, () -> posts.addComment(post.getId(), me, "x".repeat(1001)));
        assertEquals(0, posts.commentCount(post));
    }

    @Test
    void deletingThePost_removesItsComments() {
        AppUser owner = newUser("Omistaja");
        AppUser other = newUser("Toinen");
        CatchPost post = posts.create(owner, "Hauki", null, null, null, null, null, null, null);
        posts.addComment(post.getId(), other, "Kommentti");

        posts.delete(post.getId(), owner);

        assertEquals(0, posts.byAuthor(owner).size());
    }

    @Test
    void deletingAnAccount_removesItsCommentsOnOthersPosts() {
        AppUser owner = newUser("Omistaja");
        AppUser leaver = newUser("Poistuja");
        CatchPost post = posts.create(owner, "Hauki", null, null, null, null, null, null, null);
        posts.addComment(post.getId(), leaver, "Hyvä!");
        posts.addComment(post.getId(), owner, "Kiitos");

        accounts.deleteAccount(leaver.getId(), PASSWORD);

        var left = posts.comments(post);
        assertEquals(1, left.size());
        assertTrue(left.stream().allMatch(c -> c.getAuthor().equals(owner)));
    }

    @Test
    void accountEditing_changesDisplayName_andPasswordOnlyWithTheCurrentOne() {
        AppUser me = newUser("Minä");

        users.updateDisplayName(me.getId(), "  Uusi Nimi  ");
        assertEquals("Uusi Nimi", users.find(me.getUsername()).orElseThrow().getDisplayName());
        assertThrows(IllegalArgumentException.class, () -> users.updateDisplayName(me.getId(), "  "));

        String oldHash = users.find(me.getUsername()).orElseThrow().getPasswordHash();
        assertThrows(IllegalArgumentException.class, () -> users.changePassword(me.getId(), "vaara", "uusisalasana1"));
        assertThrows(IllegalArgumentException.class, () -> users.changePassword(me.getId(), PASSWORD, "lyhyt"));
        assertEquals(oldHash, users.find(me.getUsername()).orElseThrow().getPasswordHash(), "Hash ei muutu virheellä");

        users.changePassword(me.getId(), PASSWORD, "uusisalasana1");
        assertTrue(!oldHash.equals(users.find(me.getUsername()).orElseThrow().getPasswordHash()));
        users.changePassword(me.getId(), "uusisalasana1", "taasuusi123"); // uusi salasana toimii nykyisenä
    }
}
