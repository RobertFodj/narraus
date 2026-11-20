package com.narraus.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "event_message")
public class EventMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    private CompetitionEvent event;
    @ManyToOne(optional = false)
    private AppUser sender;
    @Column(nullable = false, length = 1000)
    private String text;
    @Column(nullable = false)
    private Instant sentAt = Instant.now();

    protected EventMessage() {}

    EventMessage(CompetitionEvent event, AppUser sender, String text) {
        this.event = event;
        this.sender = sender;
        this.text = text;
    }

    public AppUser getSender() { return sender; }
    public String getText() { return text; }
    public Instant getSentAt() { return sentAt; }
}
