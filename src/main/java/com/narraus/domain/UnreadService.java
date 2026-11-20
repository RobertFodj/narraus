package com.narraus.domain;

import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lukemattomien laskenta: uudet julkaisut (syöte) ja uudet viestit (keskustelut). */
@Service
public class UnreadService {

    private final PostRepository posts;
    private final MessageRepository messages;
    private final ConversationRepository conversations;
    private final ConversationReadRepository reads;
    private final UserRepository users;

    UnreadService(PostRepository posts, MessageRepository messages, ConversationRepository conversations,
                  ConversationReadRepository reads, UserRepository users) {
        this.posts = posts;
        this.messages = messages;
        this.conversations = conversations;
        this.reads = reads;
        this.users = users;
    }

    /** Muiden käyttäjien julkaisut sen jälkeen, kun syöte katsottiin viimeksi. */
    @Transactional(readOnly = true)
    public long newPosts(AppUser me) {
        Instant seen = users.findById(me.getId()).map(AppUser::getFeedSeenAt).orElse(Instant.now());
        return posts.countByAuthorNotAndCreatedAtAfter(me, seen);
    }

    @Transactional
    public void markFeedSeen(AppUser me) {
        users.findById(me.getId()).ifPresent(u -> u.setFeedSeenAt(Instant.now()));
    }

    /** Muiden lähettämät viestit kaikissa omissa keskusteluissa viimeisen lukemisen jälkeen. */
    @Transactional(readOnly = true)
    public long unreadMessages(AppUser me) {
        long total = 0;
        for (Conversation c : conversations.findForUser(me)) {
            total += unreadIn(c, me);
        }
        return total;
    }

    @Transactional(readOnly = true)
    public long unreadIn(Conversation c, AppUser me) {
        Instant after = reads.findByConversationAndUser(c, me).map(ConversationRead::getLastReadAt)
                .orElse(Instant.EPOCH);
        return messages.countByConversationAndSenderNotAndSentAtAfter(c, me, after);
    }

    /** Merkitsee keskustelun luetuksi. Palauttaa true, jos siinä oli lukemattomia (lukumäärä muuttui). */
    @Transactional
    public boolean markRead(Conversation c, AppUser me) {
        boolean hadUnread = unreadIn(c, me) > 0;
        ConversationRead r = reads.findByConversationAndUser(c, me)
                .orElseGet(() -> new ConversationRead(c, me, Instant.EPOCH));
        r.setLastReadAt(Instant.now());
        reads.save(r);
        return hadUnread;
    }
}
