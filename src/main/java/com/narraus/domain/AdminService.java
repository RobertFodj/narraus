package com.narraus.domain;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ylläpitäjän näkymä käyttäjiin: tunnukset (jotka muille ovat piilossa), kilpailut, joissa käyttäjä on mukana,
 * sekä käyttääkö hän appia vai webiä. Kaikki metodit vaativat ylläpitäjän (SecurityException muille), jottei
 * tieto vuoda vaikka käyttöliittymän tarkistus unohtuisi.
 */
@Service
public class AdminService {

    /** Yhden käyttäjän ylläpitäjälle näkyvät tiedot. */
    public record UserOverview(AppUser user, List<CompetitionEvent> events, Instant appLastUsedAt,
                               Instant webLastUsedAt, long pushDevices, long posts) {

        public String username() {
            return user.getUsername();
        }

        public boolean usesApp() {
            return appLastUsedAt != null;
        }

        public boolean usesWeb() {
            return webLastUsedAt != null;
        }

        /** Mitä käyttäjä on käyttänyt viimeisimpänä: "App" tai "Web", tai null, jos tietoa ei ole. Tasatilanteessa "App". */
        public String lastClient() {
            if (!usesApp() && !usesWeb()) return null;
            if (!usesWeb()) return "App";
            if (!usesApp()) return "Web";
            return appLastUsedAt.isBefore(webLastUsedAt) ? "Web" : "App";
        }

        /** Viimeisimmän käytön ajankohta (app tai web, kumpi on uudempi), tai null. */
        public Instant lastUsedAt() {
            if (!usesApp()) return webLastUsedAt;
            if (!usesWeb()) return appLastUsedAt;
            return appLastUsedAt.isBefore(webLastUsedAt) ? webLastUsedAt : appLastUsedAt;
        }

        /** "App", "Web", "App ja web" tai "Ei tietoa" (käyttäjä ei ole vielä avannut sovellusta tämän seurannan jälkeen). */
        public String clientLabel() {
            if (usesApp() && usesWeb()) return "App ja web";
            if (usesApp()) return "App";
            if (usesWeb()) return "Web";
            return "Ei tietoa";
        }
    }

    private final AdminPolicy admin;
    private final UserRepository users;
    private final EventParticipantRepository participants;
    private final PushSubscriptionRepository pushSubscriptions;
    private final PostRepository posts;

    AdminService(AdminPolicy admin, UserRepository users, EventParticipantRepository participants,
                 PushSubscriptionRepository pushSubscriptions, PostRepository posts) {
        this.admin = admin;
        this.users = users;
        this.participants = participants;
        this.pushSubscriptions = pushSubscriptions;
        this.posts = posts;
    }

    private void requireAdmin(AppUser requester) {
        if (!admin.isAdmin(requester)) throw new SecurityException("Vain ylläpitäjä");
    }

    /** Kaikki käyttäjät näyttönimen mukaan järjestettynä. */
    @Transactional(readOnly = true)
    public List<UserOverview> overview(AppUser requester) {
        requireAdmin(requester);
        Map<Long, List<CompetitionEvent>> eventsByUser = participants.findAll().stream()
                .collect(Collectors.groupingBy(p -> p.getUser().getId(),
                        Collectors.mapping(EventParticipant::getEvent, Collectors.toList())));
        return users.findAll().stream()
                .sorted(Comparator.comparing(u -> u.getDisplayName().toLowerCase()))
                .map(u -> build(u, eventsByUser.getOrDefault(u.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public UserOverview overviewOf(AppUser requester, Long userId) {
        requireAdmin(requester);
        AppUser user = users.findById(userId).orElseThrow(() -> new IllegalArgumentException("Käyttäjää ei löytynyt"));
        return build(user, participants.findByUser(user).stream().map(EventParticipant::getEvent).toList());
    }

    private UserOverview build(AppUser user, List<CompetitionEvent> events) {
        List<CompetitionEvent> sorted = events.stream()
                .sorted(Comparator.comparing(CompetitionEvent::getStartsAt).reversed()).toList();
        return new UserOverview(user, sorted, user.getAppLastUsedAt(), user.getWebLastUsedAt(),
                pushSubscriptions.countByUser(user), posts.countByAuthor(user));
    }
}
