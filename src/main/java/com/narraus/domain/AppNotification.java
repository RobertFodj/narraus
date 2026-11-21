package com.narraus.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** Käyttäjän sovelluksen sisäinen ilmoitus (kellokuvakkeen lista). */
@Entity
@Table(name = "app_notification")
public class AppNotification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    private AppUser recipient;
    @ManyToOne
    private AppUser actor;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationType type;
    private Long targetId;
    @Column(length = 200)
    private String label;
    @Column(nullable = false)
    private Instant createdAt = Instant.now();
    private Instant readAt;

    protected AppNotification() {}

    AppNotification(AppUser recipient, AppUser actor, NotificationType type, Long targetId, String label) {
        this.recipient = recipient;
        this.actor = actor;
        this.type = type;
        this.targetId = targetId;
        this.label = label == null ? null : (label.length() > 200 ? label.substring(0, 200) : label);
    }

    public Long getId() { return id; }
    public AppUser getRecipient() { return recipient; }
    public AppUser getActor() { return actor; }
    public NotificationType getType() { return type; }
    public Long getTargetId() { return targetId; }
    public String getLabel() { return label; }
    public Instant getCreatedAt() { return createdAt; }
    public boolean isRead() { return readAt != null; }
}
