package com.narraus.domain;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Tilin poisto: käyttäjä ja kaikki hänen tietonsa poistetaan pysyvästi. Vaatii salasanan. */
@Service
public class AccountService {

    private final UserRepository users;
    private final PostRepository posts;
    private final PostService postService;
    private final ChatService chats;
    private final LikeRepository likes;
    private final CommentRepository comments;
    private final EventService eventService;
    private final AdminPolicy admin;
    private final FollowRepository follows;
    private final PushSubscriptionRepository pushSubscriptions;
    private final PasswordEncoder encoder;
    private final PersistentTokenRepository loginTokens;
    private final SystemMessageRepository systemMessages;

    AccountService(UserRepository users, PostRepository posts, PostService postService, ChatService chats,
                   LikeRepository likes, CommentRepository comments, EventService eventService, AdminPolicy admin, FollowRepository follows, PushSubscriptionRepository pushSubscriptions,
                   PasswordEncoder encoder, PersistentTokenRepository loginTokens,
                   SystemMessageRepository systemMessages) {
        this.systemMessages = systemMessages;
        this.loginTokens = loginTokens;
        this.users = users;
        this.posts = posts;
        this.postService = postService;
        this.chats = chats;
        this.likes = likes;
        this.comments = comments;
        this.eventService = eventService;
        this.admin = admin;
        this.follows = follows;
        this.pushSubscriptions = pushSubscriptions;
        this.encoder = encoder;
    }

    /**
     * Poistaa tilin ja kaiken sen tiedot: julkaisut (kuvineen ja niiden tykkäyksineen), annetut tykkäykset,
     * seuraamiset molempiin suuntiin, viestit ja keskusteluosallisuudet sekä ilmoituslaitteet.
     * Väärä salasana keskeyttää poiston kokonaan, eikä mitään poisteta.
     */
    @Transactional
    public void deleteAccount(Long userId, String rawPassword) {
        if (userId == null) throw new IllegalArgumentException("Käyttäjää ei löytynyt");
        AppUser user = users.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Käyttäjää ei löytynyt"));
        if (rawPassword == null || rawPassword.isEmpty() || !encoder.matches(rawPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Väärä salasana");
        }
        if (admin.isAdmin(user)) {
            throw new IllegalArgumentException("Ylläpitäjän tiliä ei voi poistaa");
        }
        eventService.removeUser(user);
        for (CatchPost post : posts.findByAuthor(user)) {
            postService.delete(post.getId(), user); // poistaa myös tykkäykset ja kuvatiedoston
        }
        likes.deleteByUser(user);
        comments.deleteByAuthor(user);
        follows.deleteByFollower(user);
        follows.deleteByFollowed(user);
        pushSubscriptions.deleteByUser(user);
        systemMessages.deleteByRecipient(user); // Narraus-viestit
        chats.removeUser(user);
        loginTokens.removeUserTokens(user.getUsername()); // "pysy kirjautuneena" -tunnisteet kumoutuvat
        users.delete(user);
    }
}
