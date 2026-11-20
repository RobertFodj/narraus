package com.narraus.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "event_participant", uniqueConstraints = @UniqueConstraint(columnNames = {"event_id", "user_id"}))
public class EventParticipant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    private CompetitionEvent event;
    @ManyToOne(optional = false)
    private AppUser user;
    @Column(nullable = false)
    private Instant joinedAt = Instant.now();

    protected EventParticipant() {}

    EventParticipant(CompetitionEvent event, AppUser user) {
        this.event = event;
        this.user = user;
    }

    public AppUser getUser() { return user; }
    public Instant getJoinedAt() { return joinedAt; }
}
