package com.narraus.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatService {

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final ChatBroadcaster broadcaster;

    ChatService(ConversationRepository conversations, MessageRepository messages, ChatBroadcaster broadcaster) {
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

    public boolean isMember(Conversation c, AppUser user) {
        return c.getParticipants().contains(user);
    }

    public Conversation get(Long id, AppUser requester) {
        return conversations.findById(id).filter(c -> isMember(c, requester)).orElse(null);
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
        if (!isMember(c, sender)) throw new SecurityException("Ei oikeutta");
        if (text == null || text.isBlank()) return;
        messages.save(new ChatMessage(c, sender, text.strip()));
        c.setLastActivity(Instant.now());
        conversations.save(c);
        broadcaster.broadcast(c.getId());
    }
}
