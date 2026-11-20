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
    /** Valinnaiset käännökset (ruotsi, norja, englanti); puuttuessa näytetään suomenkielinen teksti. */
    @Column(length = 120)
    private String nameSv;
    @Column(length = 120)
    private String nameNo;
    @Column(length = 120)
    private String nameEn;
    @Column(length = 2000)
    private String descriptionSv;
    @Column(length = 2000)
    private String descriptionNo;
    @Column(length = 2000)
    private String descriptionEn;
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

    /** Kieli: "sv", "no" tai "en" (muulla arvolla suomi). Tyhjä käännös tallennetaan nulliksi. */
    void translate(String language, String name, String description) {
        String n = blankToNull(name);
        String d = blankToNull(description);
        switch (language) {
            case "sv" -> { nameSv = n; descriptionSv = d; }
            case "no" -> { nameNo = n; descriptionNo = d; }
            case "en" -> { nameEn = n; descriptionEn = d; }
            default -> throw new IllegalArgumentException("Tuntematon kieli");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** Nimi annetulla kielellä (sv, no, en); jos käännöstä ei ole, suomenkielinen nimi. */
    public String name(String language) {
        String t = switch (language == null ? "fi" : language) {
            case "sv" -> nameSv;
            case "no" -> nameNo;
            case "en" -> nameEn;
            default -> null;
        };
        return t == null ? name : t;
    }

    /** Kuvaus annetulla kielellä; jos käännöstä ei ole, suomenkielinen kuvaus (tai null). */
    public String description(String language) {
        String t = switch (language == null ? "fi" : language) {
            case "sv" -> descriptionSv;
            case "no" -> descriptionNo;
            case "en" -> descriptionEn;
            default -> null;
        };
        return t == null ? description : t;
    }

    /** Käännetty nimi sellaisenaan (null, jos ei ole); lomakkeen esitäyttöä varten. */
    public String translatedName(String language) {
        return switch (language) { case "sv" -> nameSv; case "no" -> nameNo; case "en" -> nameEn; default -> null; };
    }

    public String translatedDescription(String language) {
        return switch (language) { case "sv" -> descriptionSv; case "no" -> descriptionNo; case "en" -> descriptionEn; default -> null; };
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
