package com.narraus.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService implements UserDetailsService {

    private final UserRepository users;
    private final FollowRepository follows;
    private final PasswordEncoder encoder;

    UserService(UserRepository users, FollowRepository follows, PasswordEncoder encoder) {
        this.users = users;
        this.follows = follows;
        this.encoder = encoder;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser u = users.findByUsername(username.toLowerCase())
                .orElseThrow(() -> new UsernameNotFoundException(username));
        return User.withUsername(u.getUsername()).password(u.getPasswordHash()).roles("USER").build();
    }

    @Transactional
    public AppUser register(String username, String displayName, String rawPassword) {
        String name = username.trim().toLowerCase();
        if (name.length() < 3) throw new IllegalArgumentException("Käyttäjätunnus on liian lyhyt (väh. 3 merkkiä)");
        if (rawPassword.length() < 8) throw new IllegalArgumentException("Salasanan on oltava vähintään 8 merkkiä");
        if (users.existsByUsernameIgnoreCase(name)) throw new IllegalArgumentException("Käyttäjätunnus on jo käytössä");
        String shown = displayName == null || displayName.isBlank() ? name : displayName.trim();
        return users.save(new AppUser(name, encoder.encode(rawPassword), shown));
    }

    public Optional<AppUser> find(String username) {
        return users.findByUsername(username);
    }

    public List<AppUser> search(String query) {
        String q = query == null ? "" : query.trim();
        return users.findByDisplayNameContainingIgnoreCaseOrUsernameContainingIgnoreCaseOrderByDisplayName(
                q, q, PageRequest.of(0, 50));
    }

    // --- seuraaminen ---

    public boolean isFollowing(AppUser me, AppUser other) {
        return follows.existsByFollowerAndFollowed(me, other);
    }

    public long followerCount(AppUser user) {
        return follows.countByFollowed(user);
    }

    @Transactional
    public void toggleFollow(AppUser me, AppUser other) {
        if (me.equals(other)) return;
        if (follows.existsByFollowerAndFollowed(me, other)) follows.deleteByFollowerAndFollowed(me, other);
        else follows.save(new Follow(me, other));
    }
}
