package com.narraus.domain;

import jakarta.persistence.*;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"follower_id", "followed_id"}))
public class Follow {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    private AppUser follower;
    @ManyToOne(optional = false)
    private AppUser followed;

    protected Follow() {}
    public Follow(AppUser follower, AppUser followed) { this.follower = follower; this.followed = followed; }

    public AppUser getFollowed() { return followed; }
}
