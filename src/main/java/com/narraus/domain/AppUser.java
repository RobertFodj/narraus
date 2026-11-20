package com.narraus.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "app_user")
public class AppUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(unique = true, nullable = false)
    private String username;
    @Column(nullable = false)
    private String passwordHash;
    @Column(nullable = false)
    private String displayName;
    /** Milloin saalissyöte on viimeksi katsottu; sen jälkeen tulleet muiden julkaisut ovat "uusia". */
    @Column(nullable = false)
    private Instant feedSeenAt = Instant.now();
    /** Milloin sovellusta on viimeksi käytetty asennettuna sovelluksena (PWA) ja milloin selaimessa. */
    private Instant appLastUsedAt;
    private Instant webLastUsedAt;

    protected AppUser() {}

    public AppUser(String username, String passwordHash, String displayName) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getDisplayName() { return displayName; }
    public void setUsername(String username) { this.username = username; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public Instant getFeedSeenAt() { return feedSeenAt; }
    public Instant getAppLastUsedAt() { return appLastUsedAt; }
    public Instant getWebLastUsedAt() { return webLastUsedAt; }

    public void recordUsage(boolean installedApp, Instant when) {
        if (installedApp) appLastUsedAt = when; else webLastUsedAt = when;
    }
    public void setFeedSeenAt(Instant t) { this.feedSeenAt = t; }

    @Override public boolean equals(Object o) {
        return o instanceof AppUser u && id != null && id.equals(u.id);
    }
    @Override public int hashCode() { return 31; }
}
