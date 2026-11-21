package com.narraus.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kun ylläpitäjä poistaa tai muokkaa toisen käyttäjän julkaisua, päätös kirjataan ja käyttäjä saa ilmoituksen
 * (kellokuvake). Käyttäjä voi valittaa tai antaa palautetta päätöksestä; valitukset näkyvät ylläpitäjälle.
 */
@Service
public class ModerationService {

    public static final int MAX_APPEAL_LENGTH = 2000;
    /** Yhdestä päätöksestä voi lähettää rajallisen määrän viestejä (ei roskapostia). */
    public static final int MAX_APPEALS_PER_ACTION = 5;

    private final ModerationActionRepository actions;
    private final ModerationAppealRepository appeals;
    private final NotificationService notifications;
    private final AdminPolicy adminPolicy;
    private final UserRepository users;

    ModerationService(ModerationActionRepository actions, ModerationAppealRepository appeals,
                      NotificationService notifications, AdminPolicy adminPolicy, UserRepository users) {
        this.actions = actions;
        this.appeals = appeals;
        this.notifications = notifications;
        this.adminPolicy = adminPolicy;
        this.users = users;
    }

    /** Kirjaa päätöksen ja ilmoittaa tekijälle. Kutsutaan, kun ylläpitäjä (ei tekijä) poistaa tai muokkaa julkaisua. */
    @Transactional
    ModerationAction record(AppUser actor, ModerationKind kind, CatchPost post, String reason) {
        ModerationAction action = actions.save(new ModerationAction(post.getAuthor(), actor, kind, post, reason));
        notifications.moderated(action);
        return action;
    }

    /** Päätös näytetään vain sen kohteelle (julkaisun tekijälle) ja ylläpitäjälle. */
    @Transactional(readOnly = true)
    public Optional<ModerationAction> actionFor(Long actionId, AppUser viewer) {
        if (actionId == null || viewer == null) return Optional.empty();
        return actions.findById(actionId)
                .filter(a -> a.getAuthor().equals(viewer) || adminPolicy.isAdmin(viewer));
    }

    /** Päätöksestä jo lähetetyt viestit (vanhin ensin). */
    @Transactional(readOnly = true)
    public List<ModerationAppeal> appealsOf(ModerationAction action) {
        return appeals.findByActionOrderByCreatedAt(action);
    }

    /** Käyttäjä valittaa tai antaa palautetta päätöksestä. Vain päätöksen kohde voi lähettää. */
    @Transactional
    public ModerationAppeal appeal(Long actionId, AppUser user, String text) {
        ModerationAction action = actions.findById(actionId)
                .orElseThrow(() -> new IllegalArgumentException("Päätöstä ei löytynyt"));
        if (user == null || !action.getAuthor().equals(user)) throw new SecurityException("Ei oikeutta");
        String t = text == null ? "" : text.trim();
        if (t.isEmpty()) throw new IllegalArgumentException("Viesti ei voi olla tyhjä");
        if (t.length() > MAX_APPEAL_LENGTH) {
            throw new IllegalArgumentException("Viesti on liian pitkä (enintään " + MAX_APPEAL_LENGTH + " merkkiä)");
        }
        if (appeals.countByAction(action) >= MAX_APPEALS_PER_ACTION) {
            throw new IllegalArgumentException("Olet lähettänyt tästä päätöksestä jo useita viestejä");
        }
        ModerationAppeal appeal = appeals.save(new ModerationAppeal(action, user, t));
        users.findByUsername(adminPolicy.adminUsername()).ifPresent(a -> notifications.appealed(a, action, user));
        return appeal;
    }

    /** Kaikki valitukset ja palautteet uusin ensin; vain ylläpitäjälle. */
    @Transactional(readOnly = true)
    public List<ModerationAppeal> allAppeals(AppUser admin) {
        if (!adminPolicy.isAdmin(admin)) throw new SecurityException("Vain ylläpitäjä");
        return appeals.findTop200ByOrderByCreatedAtDesc();
    }

    /** Poistaa käyttäjän päätökset ja valitukset (tilin poisto). */
    @Transactional
    void removeUser(AppUser user) {
        appeals.deleteByAuthor(user);
        appeals.deleteByActionAuthor(user);
        actions.deleteByAuthor(user);
    }
}
