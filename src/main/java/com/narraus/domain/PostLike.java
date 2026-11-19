package com.narraus.domain;

import jakarta.persistence.*;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"post_id", "user_id"}))
public class PostLike {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    private CatchPost post;
    @ManyToOne(optional = false)
    private AppUser user;

    protected PostLike() {}
    public PostLike(CatchPost post, AppUser user) { this.post = post; this.user = user; }
}
