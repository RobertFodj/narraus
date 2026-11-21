package com.narraus.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** Ylläpitäjän päätös: toisen käyttäjän julkaisun poisto tai muokkaus (muistiinpano valitusta varten). */
@Entity
@Table(name = "moderation_action")
public class ModerationAction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    private AppUser author;
    @ManyToOne(optional = false)
    private AppUser actor;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ModerationKind kind;
    private String species;
    @Column(length = 2000)
    private String description;
    @Column(length = 500)
    private String reason;
    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected ModerationAction() {}

    ModerationAction(AppUser author, AppUser actor, ModerationKind kind, CatchPost post, String reason) {
        this.author = author;
        this.actor = actor;
        this.kind = kind;
        this.species = post.getSpecies();
        this.description = post.getDescription();
        this.reason = reason == null || reason.isBlank() ? null : reason.trim();
    }

    public Long getId() { return id; }
    public AppUser getAuthor() { return author; }
    public AppUser getActor() { return actor; }
    public ModerationKind getKind() { return kind; }
    public String getSpecies() { return species; }
    public String getDescription() { return description; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
}
