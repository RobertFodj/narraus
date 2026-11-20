package com.narraus.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "event_fish_review", uniqueConstraints = @UniqueConstraint(columnNames = {"event_id", "post_id"}))
public class EventFishReview {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    private CompetitionEvent event;
    @ManyToOne(optional = false)
    private CatchPost post;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReviewStatus status;
    @Column(length = 500)
    private String note;
    @ManyToOne(optional = false)
    private AppUser reviewedBy;
    @Column(nullable = false)
    private Instant reviewedAt = Instant.now();

    protected EventFishReview() {}

    EventFishReview(CompetitionEvent event, CatchPost post, ReviewStatus status, String note, AppUser reviewedBy) {
        this.event = event;
        this.post = post;
        set(status, note, reviewedBy);
    }

    void set(ReviewStatus status, String note, AppUser reviewedBy) {
        this.status = status;
        this.note = note;
        this.reviewedBy = reviewedBy;
        this.reviewedAt = Instant.now();
    }

    public CatchPost getPost() { return post; }
    public ReviewStatus getStatus() { return status; }
    public String getNote() { return note; }
    public AppUser getReviewedBy() { return reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
}
