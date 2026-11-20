package com.narraus.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "competition_event")
public class CompetitionEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 120)
    private String name;
    @Column(length = 2000)
    private String description;
    @Column(nullable = false)
    private Instant startsAt;
    @Column(nullable = false)
    private Instant endsAt;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventMetric metric;
    /** Jos asetettu, vain tämän lajin saaliit lasketaan (isot ja pienet kirjaimet eivät erota). */
    @Column(length = 80)
    private String species;
    /** Kuinka monta kilpailijan parasta kalaa lasketaan yhteen (1 = vain paras). Eniten kaloja -kilpailussa aina 1. */
    @Column(nullable = false)
    private int countedFish = 1;
    @ManyToOne(optional = false)
    private AppUser createdBy;
    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected CompetitionEvent() {}

    CompetitionEvent(String name, String description, Instant startsAt, Instant endsAt, EventMetric metric,
                     String species, int countedFish, AppUser createdBy) {
        this.createdBy = createdBy;
        edit(name, description, startsAt, endsAt, metric, species, countedFish);
    }

    void edit(String name, String description, Instant startsAt, Instant endsAt, EventMetric metric,
              String species, int countedFish) {
        this.name = name;
        this.description = description;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.metric = metric;
        this.species = species;
        this.countedFish = countedFish;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public EventMetric getMetric() { return metric; }
    public String getSpecies() { return species; }
    public int getCountedFish() { return countedFish; }
    public AppUser getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }

    public EventStatus status(Instant now) {
        if (now.isBefore(startsAt)) return EventStatus.UPCOMING;
        if (now.isBefore(endsAt)) return EventStatus.ONGOING;
        return EventStatus.ENDED;
    }

    public EventStatus status() {
        return status(Instant.now());
    }

    boolean matchesSpecies(CatchPost p) {
        return species == null || species.equalsIgnoreCase(p.getSpecies());
    }

    @Override public boolean equals(Object o) {
        return o instanceof CompetitionEvent e && id != null && id.equals(e.id);
    }
    @Override public int hashCode() { return 31; }
}
