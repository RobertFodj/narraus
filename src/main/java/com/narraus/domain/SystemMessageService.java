package com.narraus.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Narraus-viestit. Vain ylläpitäjä ({@link AdminPolicy}) voi lähettää: kaikille tai valituille käyttäjille.
 * Vastaanottaja saa push-ilmoituksen ja viestin, jonka lähettäjänä on "Narraus", eikä viestiin voi vastata.
 */
@Service
public class SystemMessageService {

    /** Lähettäjän nimi, jonka vastaanottaja näkee (ylläpitäjän oikeaa nimeä ei koskaan näytetä). */
    public static final String SENDER_NAME = "Narraus";
    public static final int MAX_LENGTH = 1000;

    private final AdminPolicy admin;
    private final UserRepository users;
    private final SystemMessageRepository messages;
    private final SystemMessageBroadcaster broadcaster;
    private final ApplicationEventPublisher events;

    SystemMessageService(AdminPolicy admin, UserRepository users, SystemMessageRepository messages,
                         SystemMessageBroadcaster broadcaster, ApplicationEventPublisher events) {
        this.admin = admin;
        this.users = users;
        this.messages = messages;
        this.broadcaster = broadcaster;
        this.events = events;
    }

    private void requireAdmin(AppUser sender) {
        if (!admin.isAdmin(sender)) throw new SecurityException("Vain ylläpitäjä voi lähettää Narraus-viestejä");
    }

    private static String clean(String text) {
        String t = text == null ? "" : text.strip();
        if (t.isEmpty()) throw new IllegalArgumentException("Viesti ei voi olla tyhjä");
        if (t.length() > MAX_LENGTH) throw new IllegalArgumentException("Viesti on liian pitkä (enintään " + MAX_LENGTH + " merkkiä)");
        return t;
    }

    /** Lähettää viestin valituille käyttäjille. Palauttaa vastaanottajien määrän. */
    @Transactional
    public int send(AppUser sender, Collection<AppUser> recipients, String text) {
        requireAdmin(sender);
        String body = clean(text);
        Set<Long> ids = recipients == null ? Set.of() : recipients.stream().filter(Objects::nonNull)
                .map(AppUser::getId).collect(Collectors.toCollection(LinkedHashSet::new));
        if (ids.isEmpty()) throw new IllegalArgumentException("Valitse vähintään yksi vastaanottaja");
        return deliver(sender, users.findAllById(ids), body);
    }

    /** Lähettää viestin kaikille muille käyttäjille (ei lähettäjälle itselleen). */
    @Transactional
    public int sendToAll(AppUser sender, String text) {
        requireAdmin(sender);
        String body = clean(text);
        List<AppUser> everyone = users.findAll().stream().filter(u -> !u.equals(sender)).toList();
        if (everyone.isEmpty()) throw new IllegalArgumentException("Ei vastaanottajia");
        return deliver(sender, everyone, body);
    }

    private int deliver(AppUser sender, List<AppUser> targets, String text) {
        if (targets.isEmpty()) throw new IllegalArgumentException("Valitse vähintään yksi vastaanottaja");
        List<SystemMessage> rows = targets.stream().map(u -> new SystemMessage(u, sender, text)).toList();
        messages.saveAll(rows);
        targets.forEach(u -> broadcaster.broadcast(u.getId()));
        events.publishEvent(new PushEvents.SystemMessage(
                targets.stream().map(AppUser::getId).collect(Collectors.toSet()), text));
        return targets.size();
    }

    /** Käyttäjän Narraus-viestit vanhimmasta uusimpaan (viimeiset 200). */
    @Transactional(readOnly = true)
    public List<SystemMessage> messagesFor(AppUser user) {
        List<SystemMessage> list = new ArrayList<>(messages.findTop200ByRecipientOrderByCreatedAtDesc(user));
        Collections.reverse(list);
        return list;
    }

    @Transactional(readOnly = true)
    public boolean hasMessages(AppUser user) {
        return messages.countByRecipient(user) > 0;
    }

    @Transactional(readOnly = true)
    public long unreadCount(AppUser user) {
        return messages.countByRecipientAndReadAtIsNull(user);
    }

    /** Merkitsee kaikki käyttäjän Narraus-viestit luetuiksi. Palauttaa true, jos lukemattomia oli. */
    @Transactional
    public boolean markAllRead(AppUser user) {
        return messages.markAllRead(user, Instant.now()) > 0;
    }
}
