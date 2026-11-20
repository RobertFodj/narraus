package com.narraus.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatService {

    private static final int MAX_MESSAGE_LENGTH = 2000;

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final ConversationReadRepository reads;
    private final ChatBroadcaster broadcaster;
    private final ApplicationEventPublisher events;

    ChatService(ConversationRepository conversations, MessageRepository messages, ConversationReadRepository reads,
                ChatBroadcaster broadcaster, ApplicationEventPublisher events) {
        this.reads = reads;
        this.events = events;
        this.conversations = conversations;
        this.messages = messages;
        this.broadcaster = broadcaster;
    }

    public List<Conversation> conversationsOf(AppUser user) {
        return conversations.findForUser(user);
    }

    @Transactional
    public Conversation openDirect(AppUser a, AppUser b) {
        return conversations.findDirect(a, b).stream().findFirst()
                .orElseGet(() -> conversations.save(new Conversation(null, false, new HashSet<>(Set.of(a, b)))));
    }

    @Transactional
    public Conversation createGroup(AppUser owner, String name, Collection<AppUser> members) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Ryhmälle tarvitaan nimi");
        Set<AppUser> all = new HashSet<>(members);
        all.add(owner);
        Conversation c = conversations.save(new Conversation(name.trim(), true, all));
        broadcaster.broadcast(c.getId());
        return c;
    }

    /**
     * Muokkaa ryhmän nimeä ja jäseniä. Muokkaaja pitää olla ryhmän jäsen, ja hän pysyy aina jäsenenä.
     * Kuka tahansa jäsen saa muokata ryhmää.
     */
    @Transactional
    public Conversation updateGroup(Long conversationId, AppUser requester, String name, Collection<AppUser> members) {
        Conversation c = conversations.findById(conversationId)
                .filter(x -> x.getParticipants().contains(requester))
                .orElseThrow(() -> new SecurityException("Ei oikeutta"));
        if (!c.isGroupChat()) throw new IllegalArgumentException("Vain ryhmää voi muokata");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Ryhmälle tarvitaan nimi");
        Set<AppUser> all = new HashSet<>(members);
        all.add(requester);
        c.setName(name.trim());
        c.getParticipants().clear();
        c.getParticipants().addAll(all);
        conversations.save(c);
        broadcaster.broadcast(c.getId());
        return c;
    }

    /** Onko käyttäjä keskustelun jäsen juuri nyt (haetaan tuoreena, jotta vanhentunut kopio ei oikeuta). */
    public boolean isMember(Conversation c, AppUser user) {
        return c != null && c.getId() != null && conversations.findById(c.getId())
                .map(fresh -> fresh.getParticipants().contains(user)).orElse(false);
    }

    public Conversation get(Long id, AppUser requester) {
        return conversations.findById(id).filter(c -> c.getParticipants().contains(requester)).orElse(null);
    }

    /** Viimeiset 200 viestiä vanhimmasta uusimpaan. */
    public List<ChatMessage> messages(Conversation c, AppUser requester) {
        if (!isMember(c, requester)) throw new SecurityException("Ei oikeutta");
        List<ChatMessage> list = new ArrayList<>(messages.findTop200ByConversationOrderBySentAtDesc(c));
        Collections.reverse(list);
        return list;
    }

    @Transactional
    public void send(Conversation c, AppUser sender, String text) {
        // Aina tuore keskustelu: vanhentunut kopio ei saa kirjoittaa jäsenlistaa takaisin tietokantaan
        Conversation fresh = conversations.findById(c.getId())
                .filter(x -> x.getParticipants().contains(sender))
                .orElseThrow(() -> new SecurityException("Ei oikeutta"));
        if (text == null || text.isBlank()) return;
        messages.save(new ChatMessage(fresh, sender, text.strip()));
        fresh.setLastActivity(Instant.now());
        broadcaster.broadcast(fresh.getId());
        Set<Long> recipients = fresh.getParticipants().stream().filter(p -> !p.equals(sender))
                .map(AppUser::getId).collect(Collectors.toSet());
        events.publishEvent(new PushEvents.MessageSent(fresh.getId(), sender.getId(), sender.getDisplayName(),
                fresh.isGroupChat() ? fresh.getName() : null, text.strip(), recipients));
    }

    /** Käyttäjän omat viimeisimmät viestit keskustelussa (enintään 20), uusin ensin. */
    public List<ChatMessage> myRecentMessages(Conversation c, AppUser me) {
        if (!isMember(c, me)) throw new SecurityException("Ei oikeutta");
        return messages.findTop20ByConversationAndSenderOrderBySentAtDesc(c, me);
    }

    /** Muokkaa omaa viestiä. Vain viestin lähettäjä saa muokata, ja vain jos on yhä keskustelun jäsen. */
    @Transactional
    public ChatMessage editMessage(Long messageId, AppUser requester, String newText) {
        ChatMessage m = messages.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Viestiä ei löytynyt"));
        if (requester == null || !m.getSender().equals(requester)
                || !m.getConversation().getParticipants().contains(requester)) {
            throw new SecurityException("Ei oikeutta");
        }
        String text = newText == null ? "" : newText.strip();
        if (text.isEmpty()) throw new IllegalArgumentException("Viesti ei voi olla tyhjä");
        if (text.length() > MAX_MESSAGE_LENGTH) throw new IllegalArgumentException("Viesti on liian pitkä");
        if (text.equals(m.getText())) return m; // ei muutosta, ei "muokattu"-merkintää
        m.edit(text);
        messages.save(m);
        broadcaster.broadcast(m.getConversation().getId());
        return m;
    }

    /**
     * Poistaa käyttäjän keskusteluista tilin poiston yhteydessä: hänen viestinsä poistetaan kaikkialta,
     * ryhmistä hänet poistetaan jäsenistä (tyhjentynyt ryhmä poistetaan) ja suorat keskustelut poistetaan kokonaan.
     */
    @Transactional
    public void removeUser(AppUser user) {
        for (Conversation c : conversations.findForUser(user)) {
            Long id = c.getId();
            if (!c.isGroupChat()) {
                deleteConversation(c);
            } else {
                messages.deleteBySenderAndConversation(user, c);
                c.getParticipants().remove(user);
                if (c.getParticipants().isEmpty()) {
                    deleteConversation(c);
                } else {
                    conversations.save(c);
                }
            }
            broadcaster.broadcast(id);
        }
        reads.deleteByUser(user);
    }

    private void deleteConversation(Conversation c) {
        reads.deleteByConversation(c);
        messages.deleteByConversation(c);
        conversations.delete(c);
    }
}
