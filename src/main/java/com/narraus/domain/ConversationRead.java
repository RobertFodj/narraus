package com.narraus.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** Milloin käyttäjä on viimeksi lukenut tietyn keskustelun. Sen jälkeen tulleet muiden viestit ovat lukemattomia. */
@Entity
@Table(name = "conversation_read", uniqueConstraints = @UniqueConstraint(columnNames = {"conversation_id", "user_id"}))
public class ConversationRead {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    private Conversation conversation;
    @ManyToOne(optional = false)
    private AppUser user;
    @Column(nullable = false)
    private Instant lastReadAt;

    protected ConversationRead() {}

    public ConversationRead(Conversation conversation, AppUser user, Instant lastReadAt) {
        this.conversation = conversation;
        this.user = user;
        this.lastReadAt = lastReadAt;
    }

    public Instant getLastReadAt() { return lastReadAt; }
    public void setLastReadAt(Instant t) { this.lastReadAt = t; }
}
