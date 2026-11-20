package com.narraus.domain;

import java.time.Instant;
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
    private final AdminPolicy admin;

    UserService(UserRepository users, FollowRepository follows, PasswordEncoder encoder, AdminPolicy admin) {
        this.users = users;
        this.follows = follows;
        this.encoder = encoder;
        this.admin = admin;
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
        // Näyttönimi on pakollinen: se näkyy muille, joten sitä ei saa täyttää käyttäjätunnuksella
        if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("Näyttönimi vaaditaan");
        return users.save(new AppUser(name, encoder.encode(rawPassword), displayName.trim()));
    }

    /** Vaihtaa näyttönimen. Nimi on pakollinen, koska se näkyy muille. */
    @Transactional
    public AppUser updateDisplayName(Long userId, String displayName) {
        if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("Näyttönimi vaaditaan");
        AppUser user = users.findById(userId).orElseThrow(() -> new IllegalArgumentException("Käyttäjää ei löytynyt"));
        user.setDisplayName(displayName.trim());
        return users.save(user);
    }

    /**
     * Tallentaa tilin tiedot kerralla: kaikki tarkistetaan ennen kuin mitään muutetaan. Käyttäjätunnuksen tai
     * salasanan vaihto vaatii nykyisen salasanan. Tyhjä uusi salasana tarkoittaa, ettei salasanaa vaihdeta.
     *
     * @return true, jos käyttäjätunnus vaihtui (istunto on silloin kirjattava ulos, koska se perustuu tunnukseen)
     */
    @Transactional
    public boolean updateAccount(Long userId, String displayName, String newUsername, String currentPassword,
                                 String newPassword) {
        AppUser user = users.findById(userId).orElseThrow(() -> new IllegalArgumentException("Käyttäjää ei löytynyt"));
        if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("Näyttönimi vaaditaan");

        String username = newUsername == null ? user.getUsername() : newUsername.trim().toLowerCase();
        boolean usernameChanged = !username.equals(user.getUsername());
        boolean passwordChanged = newPassword != null && !newPassword.isEmpty();

        if (usernameChanged || passwordChanged) {
            if (currentPassword == null || currentPassword.isEmpty()
                    || !encoder.matches(currentPassword, user.getPasswordHash())) {
                throw new IllegalArgumentException("Nykyinen salasana on väärä");
            }
        }
        if (usernameChanged && admin.isAdmin(user)) {
            throw new IllegalArgumentException("Ylläpitäjän käyttäjätunnusta ei voi vaihtaa");
        }
        if (usernameChanged && admin.isAdminUsername(username)) {
            throw new IllegalArgumentException("Käyttäjätunnus on varattu");
        }
        if (usernameChanged) {
            if (username.length() < 3) throw new IllegalArgumentException("Käyttäjätunnus on liian lyhyt (väh. 3 merkkiä)");
            if (users.existsByUsernameIgnoreCase(username)) throw new IllegalArgumentException("Käyttäjätunnus on jo käytössä");
        }
        if (passwordChanged && newPassword.length() < 8) {
            throw new IllegalArgumentException("Salasanan on oltava vähintään 8 merkkiä");
        }

        user.setDisplayName(displayName.trim());
        if (usernameChanged) user.setUsername(username);
        if (passwordChanged) user.setPasswordHash(encoder.encode(newPassword));
        users.save(user);
        return usernameChanged;
    }

    /** Vaihtaa salasanan. Vaatii nykyisen salasanan. */
    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        AppUser user = users.findById(userId).orElseThrow(() -> new IllegalArgumentException("Käyttäjää ei löytynyt"));
        if (currentPassword == null || currentPassword.isEmpty()
                || !encoder.matches(currentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Nykyinen salasana on väärä");
        }
        if (newPassword == null || newPassword.length() < 8) {
            throw new IllegalArgumentException("Salasanan on oltava vähintään 8 merkkiä");
        }
        user.setPasswordHash(encoder.encode(newPassword));
        users.save(user);
    }

    public Optional<AppUser> find(String username) {
        return users.findByUsername(username);
    }

    public Optional<AppUser> findById(Long id) {
        return id == null ? Optional.empty() : users.findById(id);
    }

    /** Hakee käyttäjiä näyttönimellä. Kirjautumistunnuksella ei voi hakea, jottei tunnus paljastu. */
    public List<AppUser> search(String query) {
        String q = query == null ? "" : query.trim();
        return users.findByDisplayNameContainingIgnoreCaseOrderByDisplayName(q, PageRequest.of(0, 50));
    }

    /**
     * Hakee muita käyttäjiä näyttönimen osalla sivuttain (esim. ryhmän jäsenvalintaan). Ei sisällä käyttäjää itseään.
     * Tunnuksella ei voi hakea. offset ja limit voivat olla mitä tahansa.
     */
    public List<AppUser> searchOthers(String query, AppUser me, int offset, int limit) {
        String q = query == null ? "" : query.trim();
        if (limit <= 0) return List.of();
        return users.findByDisplayNameContainingIgnoreCaseAndIdNotOrderByDisplayName(
                        q, me.getId(), PageRequest.of(0, offset + limit)).stream()
                .skip(offset).limit(limit).toList();
    }

    /** Kuinka monta muuta käyttäjää haku löytää (sivutetun valinnan kokonaismäärä). */
    public int countOthers(String query, AppUser me) {
        String q = query == null ? "" : query.trim();
        return (int) users.countByDisplayNameContainingIgnoreCaseAndIdNot(q, me.getId());
    }

    /** Kuinka usein sama laitetyyppi kirjataan uudelleen (vähentää kirjoituksia). */
    private static final java.time.Duration USAGE_RESOLUTION = java.time.Duration.ofMinutes(10);

    /**
     * Kirjaa, että käyttäjä käyttää sovellusta asennettuna sovelluksena (installedApp) tai selaimessa.
     * Päivittää enintään kerran kymmenessä minuutissa laitetyyppiä kohti.
     */
    @Transactional
    public void recordClient(Long userId, boolean installedApp) {
        if (userId == null) return;
        users.findById(userId).ifPresent(u -> {
            Instant now = Instant.now();
            Instant last = installedApp ? u.getAppLastUsedAt() : u.getWebLastUsedAt();
            if (last == null || last.isBefore(now.minus(USAGE_RESOLUTION))) u.recordUsage(installedApp, now);
        });
    }

    // --- seuraaminen ---

    public long followingCount(AppUser user) {
        return follows.countByFollower(user);
    }

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
