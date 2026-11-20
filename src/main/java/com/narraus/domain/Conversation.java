package com.narraus.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
public class Conversation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private boolean groupChat;
    private Instant lastActivity = Instant.now();
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "conversation_participants")
    private Set<AppUser> participants = new HashSet<>();

    protected Conversation() {}

    public Conversation(String name, boolean groupChat, Set<AppUser> participants) {
        this.name = name;
        this.groupChat = groupChat;
        this.participants = participants;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isGroupChat() { return groupChat; }
    public Instant getLastActivity() { return lastActivity; }
    public void setLastActivity(Instant t) { this.lastActivity = t; }
    public Set<AppUser> getParticipants() { return participants; }

    /** Nimi, joka näytetään katsojalle. */
    public String titleFor(AppUser viewer) {
        if (groupChat) return name;
        return participants.stream().filter(p -> !p.equals(viewer))
                .map(AppUser::getDisplayName).findFirst().orElse("(tyhjä)");
    }

    @Override public boolean equals(Object o) {
        return o instanceof Conversation c && id != null && id.equals(c.id);
    }
    @Override public int hashCode() { return 31; }
}
