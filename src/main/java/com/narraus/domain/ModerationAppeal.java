package com.narraus.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** Käyttäjän valitus tai palaute ylläpitäjän päätöksestä. */
@Entity
@Table(name = "moderation_appeal")
public class ModerationAppeal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    private ModerationAction action;
    @ManyToOne(optional = false)
    private AppUser author;
    @Column(nullable = false, length = 2000)
    private String text;
    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected ModerationAppeal() {}

    ModerationAppeal(ModerationAction action, AppUser author, String text) {
        this.action = action;
        this.author = author;
        this.text = text;
    }

    public Long getId() { return id; }
    public ModerationAction getAction() { return action; }
    public AppUser getAuthor() { return author; }
    public String getText() { return text; }
    public Instant getCreatedAt() { return createdAt; }
}
