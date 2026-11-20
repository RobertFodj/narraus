package com.narraus.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** Narraus-viesti: ylläpitäjän lähettämä viesti, jonka lähettäjänä vastaanottajalle näkyy "Narraus". */
@Entity
@Table(name = "system_message")
public class SystemMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    private AppUser recipient;
    /** Ylläpitäjä, joka lähetti viestin. Ei koskaan näytetä vastaanottajalle. */
    @ManyToOne
    private AppUser sentBy;
    @Column(nullable = false, length = 1000)
    private String text;
    @Column(nullable = false)
    private Instant createdAt = Instant.now();
    private Instant readAt;

    protected SystemMessage() {}

    SystemMessage(AppUser recipient, AppUser sentBy, String text) {
        this.recipient = recipient;
        this.sentBy = sentBy;
        this.text = text;
    }

    public Long getId() { return id; }
    public String getText() { return text; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReadAt() { return readAt; }
    public boolean isRead() { return readAt != null; }
}
