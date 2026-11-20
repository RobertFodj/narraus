package com.narraus.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** Yhden laitteen tai selaimen web push -tilaus. */
@Entity
@Table(name = "push_subscription")
public class PushSubscriptionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    private AppUser user;
    @Column(nullable = false, length = 2048, unique = true)
    private String endpoint;
    @Column(nullable = false)
    private String p256dh;
    @Column(nullable = false)
    private String auth;
    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected PushSubscriptionEntity() {}

    public PushSubscriptionEntity(AppUser user, String endpoint, String p256dh, String auth) {
        this.user = user;
        this.endpoint = endpoint;
        this.p256dh = p256dh;
        this.auth = auth;
    }

    public Long getId() { return id; }
    public AppUser getUser() { return user; }
    public String getEndpoint() { return endpoint; }
    public String getP256dh() { return p256dh; }
    public String getAuth() { return auth; }

    /** Sama laite kirjautui toisella käyttäjällä: tilaus siirtyy uudelle käyttäjälle. */
    public void reassign(AppUser user, String p256dh, String auth) {
        this.user = user;
        this.p256dh = p256dh;
        this.auth = auth;
    }
}
