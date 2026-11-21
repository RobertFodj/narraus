package com.narraus.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sovelluksen sisäiset ilmoitukset (kellokuvake): uusi seuraaja, tykkäys ja uusi kilpailu. Samassa listassa näkyvät
 * myös Narraus-viestit, jotka säilyvät silti omassa paikassaan Keskusteluissa. Chat-viestit eivät ole tässä.
 */
@Service
public class NotificationService {

    /** Saman seuraajan toistuvaa seuraamista (seuraa/lopeta/seuraa) ei ilmoiteta uudelleen tämän ajan sisällä. */
    private static final Duration FOLLOW_DEDUP = Duration.ofHours(24);
    private static final int LIMIT = 50;

    /** Rivi ilmoituslistassa. {@code kind} on ilmoituksen laji tai null, jos kyse on Narraus-viestistä. */
    public record Entry(NotificationType kind, boolean systemMessage, AppUser actor, Long targetId, String label,
                        Instant createdAt, boolean unread) {}

    private final NotificationRepository notifications;
    private final SystemMessageRepository systemMessages;
    private final UserRepository users;
    private final NotificationBroadcaster broadcaster;

    NotificationService(NotificationRepository notifications, SystemMessageRepository systemMessages,
                        UserRepository users, NotificationBroadcaster broadcaster) {
        this.notifications = notifications;
        this.systemMessages = systemMessages;
        this.users = users;
        this.broadcaster = broadcaster;
    }

    /** {@code actor} alkoi seurata käyttäjää {@code recipient}. */
    @Transactional
    public void newFollower(AppUser recipient, AppUser actor) {
        if (recipient.equals(actor)) return;
        if (notifications.existsByRecipientAndActorAndTypeAndCreatedAtAfter(recipient, actor,
                NotificationType.FOLLOWER, Instant.now().minus(FOLLOW_DEDUP))) return;
        save(new AppNotification(recipient, actor, NotificationType.FOLLOWER, actor.getId(), null));
    }

    /** {@code actor} tykkäsi julkaisusta; sama käyttäjä ja julkaisu ilmoitetaan vain kerran. */
    @Transactional
    public void newLike(AppUser recipient, AppUser actor, CatchPost post) {
        if (recipient.equals(actor)) return;
        if (notifications.existsByRecipientAndActorAndTypeAndTargetId(recipient, actor, NotificationType.LIKE,
                post.getId())) return;
        save(new AppNotification(recipient, actor, NotificationType.LIKE, post.getId(), post.getSpecies()));
    }

    /** Uusi kilpailu kaikille muille käyttäjille kuin sen luoneelle. */
    @Transactional
    public void newEvent(CompetitionEvent event, AppUser creator) {
        for (AppUser u : users.findAll()) {
            if (u.equals(creator)) continue;
            save(new AppNotification(u, creator, NotificationType.EVENT, event.getId(), event.getName()));
        }
    }

    /** Ylläpitäjä poisti tai muokkasi käyttäjän julkaisua: käyttäjä saa ilmoituksen ja voi valittaa. */
    @Transactional
    public void moderated(ModerationAction action) {
        NotificationType type = action.getKind() == ModerationKind.DELETED
                ? NotificationType.MODERATION_DELETED : NotificationType.MODERATION_EDITED;
        save(new AppNotification(action.getAuthor(), action.getActor(), type, action.getId(), action.getSpecies()));
    }

    /** Käyttäjä lähetti valituksen tai palautteen: ilmoitus ylläpitäjälle. */
    @Transactional
    public void appealed(AppUser admin, ModerationAction action, AppUser from) {
        save(new AppNotification(admin, from, NotificationType.APPEAL, action.getId(), action.getSpecies()));
    }

    private void save(AppNotification n) {
        notifications.save(n);
        broadcaster.broadcast(n.getRecipient().getId());
    }

    /** Uusimmat ilmoitukset ja Narraus-viestit yhdessä, uusin ensin (enintään {@value #LIMIT}). */
    @Transactional(readOnly = true)
    public List<Entry> recent(AppUser user) {
        List<Entry> all = new ArrayList<>();
        for (AppNotification n : notifications.findTop50ByRecipientOrderByCreatedAtDesc(user)) {
            all.add(new Entry(n.getType(), false, n.getActor(), n.getTargetId(), n.getLabel(), n.getCreatedAt(),
                    !n.isRead()));
        }
        for (SystemMessage m : systemMessages.findTop200ByRecipientOrderByCreatedAtDesc(user)) {
            all.add(new Entry(null, true, null, m.getId(), m.getText(), m.getCreatedAt(), !m.isRead()));
        }
        all.sort(Comparator.comparing(Entry::createdAt).reversed());
        return all.size() > LIMIT ? all.subList(0, LIMIT) : all;
    }

    /** Lukemattomat ilmoitukset ja Narraus-viestit yhteensä (kellokuvakkeen merkki). */
    @Transactional(readOnly = true)
    public long unreadCount(AppUser user) {
        return notifications.countByRecipientAndReadAtIsNull(user) + systemMessages.countByRecipientAndReadAtIsNull(user);
    }

    /** Merkitsee kaikki ilmoitukset ja Narraus-viestit luetuiksi (lista on avattu). Palauttaa true, jos jokin muuttui. */
    @Transactional
    public boolean markAllRead(AppUser user) {
        Instant now = Instant.now();
        int changed = notifications.markAllRead(user, now) + systemMessages.markAllRead(user, now);
        return changed > 0;
    }

    /** Poistaa tilin ilmoitukset: omat sekä ne, joiden aiheuttaja käyttäjä on (tilin poisto). */
    @Transactional
    public void removeUser(AppUser user) {
        notifications.deleteByRecipient(user);
        notifications.deleteByActor(user);
    }
}
