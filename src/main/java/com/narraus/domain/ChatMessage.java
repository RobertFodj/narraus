package com.narraus.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
public class ChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    private Conversation conversation;
    @ManyToOne(optional = false)
    private AppUser sender;
    @Column(nullable = false, length = 2000)
    private String text;
    @Column(nullable = false)
    private Instant sentAt = Instant.now();
    /** Milloin viestiä on viimeksi muokattu; null, jos ei ole muokattu. */
    private Instant editedAt;

    protected ChatMessage() {}
    public ChatMessage(Conversation conversation, AppUser sender, String text) {
        this.conversation = conversation;
        this.sender = sender;
        this.text = text;
    }

    public Long getId() { return id; }
    public Conversation getConversation() { return conversation; }
    public AppUser getSender() { return sender; }
    public String getText() { return text; }
    public Instant getSentAt() { return sentAt; }
    public Instant getEditedAt() { return editedAt; }
    public boolean isEdited() { return editedAt != null; }

    public void edit(String newText) {
        this.text = newText;
        this.editedAt = Instant.now();
    }
}
