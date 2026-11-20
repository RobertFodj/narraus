package com.narraus.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
public class CatchPost {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    private AppUser author;
    @Column(nullable = false)
    private String species;
    private Double weightKg;
    private Double lengthCm;
    private Double girthCm;
    private String location;
    @Column(length = 2000)
    private String description;
    private String photoFile;
    /** Sijainti kartalla (WGS84), valinnainen. */
    private Double latitude;
    private Double longitude;
    @Column(nullable = false)
    private Instant createdAt = Instant.now();
    /** Milloin kala on pyydetty. Oletuksena julkaisuhetki, mutta käyttäjä voi muuttaa sen. */
    @Column(nullable = false)
    private Instant caughtAt = Instant.now();

    protected CatchPost() {}

    public CatchPost(AppUser author, String species, Double weightKg, Double lengthCm, Double girthCm,
                     String location, String description, String photoFile) {
        this.author = author;
        this.species = species;
        this.weightKg = weightKg;
        this.lengthCm = lengthCm;
        this.girthCm = girthCm;
        this.location = location;
        this.description = description;
        this.photoFile = photoFile;
    }

    public Long getId() { return id; }
    public AppUser getAuthor() { return author; }
    public String getSpecies() { return species; }
    public Double getWeightKg() { return weightKg; }
    public Double getLengthCm() { return lengthCm; }
    public Double getGirthCm() { return girthCm; }
    public String getLocation() { return location; }
    public String getDescription() { return description; }
    public String getPhotoFile() { return photoFile; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCaughtAt() { return caughtAt; }
    public void setCaughtAt(Instant caughtAt) { this.caughtAt = caughtAt; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public boolean hasCoordinates() { return latitude != null && longitude != null; }

    /** Päivittää julkaisun muokattavat tiedot (tekijä ja luontiaika eivät muutu). */
    public void edit(String species, Double weightKg, Double lengthCm, Double girthCm,
                     String location, String description) {
        this.species = species;
        this.weightKg = weightKg;
        this.lengthCm = lengthCm;
        this.girthCm = girthCm;
        this.location = location;
        this.description = description;
    }

    public void setPhotoFile(String photoFile) { this.photoFile = photoFile; }

    public void setCoordinates(Double latitude, Double longitude) {
        boolean both = latitude != null && longitude != null;
        this.latitude = both ? latitude : null;
        this.longitude = both ? longitude : null;
    }

    @Override public boolean equals(Object o) {
        return o instanceof CatchPost p && id != null && id.equals(p.id);
    }
    @Override public int hashCode() { return 31; }
}
