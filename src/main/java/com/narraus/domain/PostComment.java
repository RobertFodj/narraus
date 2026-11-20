package com.narraus.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
public class PostComment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    private CatchPost post;
    @ManyToOne(optional = false)
    private AppUser author;
    @Column(nullable = false, length = 1000)
    private String text;
    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected PostComment() {}

    public PostComment(CatchPost post, AppUser author, String text) {
        this.post = post;
        this.author = author;
        this.text = text;
    }

    public Long getId() { return id; }
    public CatchPost getPost() { return post; }
    public AppUser getAuthor() { return author; }
    public String getText() { return text; }
    public Instant getCreatedAt() { return createdAt; }
}
