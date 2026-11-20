package com.narraus.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kilpailut (eventit). Kuka tahansa kirjautunut voi liittyä, kilpailla ja keskustella. Vain ylläpitäjä
 * ({@link AdminPolicy}) voi luoda, muokata ja poistaa kilpailuja sekä poistaa kaloja tuloksista tai kysyä, onko kala oikea.
 *
 * <p>Saalis lasketaan kilpailuun vain, jos: tekijä on liittynyt kilpailuun, saaliin pyyntiaika on kilpailun aikavälillä,
 * <b>saaliissa on kuva</b>, laji täsmää (jos kilpailulla on lajirajaus), kilpailun mittari (paino, pituus tai
 * ympärysmitta) on annettu eikä ylläpitäjä ole poistanut kalaa kilpailusta.
 */
@Service
public class EventService {

    private static final Locale FI = Locale.of("fi", "FI");

    /** Tuloksiin lasketty kala. {@code review} on null, jos ylläpitäjä ei ole merkinnyt sitä. */
    public record CountedFish(CatchPost post, double value, EventFishReview review) {
        public boolean questioned() {
            return review != null && review.getStatus() == ReviewStatus.QUESTIONED;
        }
    }

    public record Standing(int rank, AppUser user, double score, List<CountedFish> fish) {}

    public record Leaderboard(CompetitionEvent event, List<Standing> standings, List<EventFishReview> rejected,
                              int participants) {}

    private final CompetitionEventRepository events;
    private final EventParticipantRepository participants;
    private final EventFishReviewRepository reviews;
    private final EventMessageRepository messages;
    private final PostRepository posts;
    private final AdminPolicy admin;
    private final EventBroadcaster broadcaster;

    EventService(CompetitionEventRepository events, EventParticipantRepository participants,
                 EventFishReviewRepository reviews, EventMessageRepository messages, PostRepository posts,
                 AdminPolicy admin, EventBroadcaster broadcaster) {
        this.events = events;
        this.participants = participants;
        this.reviews = reviews;
        this.messages = messages;
        this.posts = posts;
        this.admin = admin;
        this.broadcaster = broadcaster;
    }

    public boolean isAdmin(AppUser user) {
        return admin.isAdmin(user);
    }

    private void requireAdmin(AppUser actor) {
        if (!admin.isAdmin(actor)) throw new SecurityException("Vain ylläpitäjä voi hallita kilpailuja");
    }

    // ---- kilpailun hallinta (vain ylläpitäjä) ----

    @Transactional
    public CompetitionEvent create(AppUser actor, String name, String description, Instant startsAt, Instant endsAt,
                                   EventMetric metric, String species, int countedFish) {
        requireAdmin(actor);
        Valid v = validate(name, description, startsAt, endsAt, metric, species, countedFish);
        return events.save(new CompetitionEvent(v.name, v.description, startsAt, endsAt, metric, v.species,
                v.countedFish, actor));
    }

    @Transactional
    public CompetitionEvent update(Long eventId, AppUser actor, String name, String description, Instant startsAt,
                                   Instant endsAt, EventMetric metric, String species, int countedFish) {
        requireAdmin(actor);
        CompetitionEvent event = require(eventId);
        Valid v = validate(name, description, startsAt, endsAt, metric, species, countedFish);
        event.edit(v.name, v.description, startsAt, endsAt, metric, v.species, v.countedFish);
        CompetitionEvent saved = events.save(event);
        broadcaster.broadcast(eventId);
        return saved;
    }

    /** Kilpailun nimen ja kuvauksen käännös yhdelle kielelle ("sv", "no" tai "en"). */
    public record Translation(String name, String description) {}

    /**
     * Tallentaa kilpailun nimen ja kuvauksen käännökset (vain ylläpitäjä). Tyhjä käännös poistaa käännöksen,
     * jolloin näytetään suomenkielinen teksti.
     */
    @Transactional
    public CompetitionEvent translate(Long eventId, AppUser actor, java.util.Map<String, Translation> byLanguage) {
        requireAdmin(actor);
        CompetitionEvent event = require(eventId);
        for (var e : byLanguage.entrySet()) {
            Translation t = e.getValue();
            String n = t.name() == null ? "" : t.name().trim();
            String d = t.description() == null ? "" : t.description().trim();
            if (n.length() > 120) throw new IllegalArgumentException("Nimi on liian pitkä (enintään 120 merkkiä)");
            if (d.length() > 2000) throw new IllegalArgumentException("Kuvaus on liian pitkä (enintään 2000 merkkiä)");
            event.translate(e.getKey(), n, d);
        }
        CompetitionEvent saved = events.save(event);
        broadcaster.broadcast(eventId);
        return saved;
    }

    /** Poistaa kilpailun osallistujineen, tarkistuksineen ja keskusteluineen. Saaliit säilyvät. */
    @Transactional
    public void delete(Long eventId, AppUser actor) {
        requireAdmin(actor);
        CompetitionEvent event = require(eventId);
        reviews.deleteByEvent(event);
        messages.deleteByEvent(event);
        participants.deleteByEvent(event);
        events.delete(event);
        broadcaster.broadcast(eventId);
    }

    private record Valid(String name, String description, String species, int countedFish) {}

    private static Valid validate(String name, String description, Instant startsAt, Instant endsAt,
                                  EventMetric metric, String species, int countedFish) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) throw new IllegalArgumentException("Kilpailulle tarvitaan nimi");
        if (n.length() > 120) throw new IllegalArgumentException("Nimi on liian pitkä (enintään 120 merkkiä)");
        if (startsAt == null || endsAt == null) throw new IllegalArgumentException("Anna alkamis- ja päättymisaika");
        if (!endsAt.isAfter(startsAt)) throw new IllegalArgumentException("Päättymisen on oltava alkamisen jälkeen");
        if (metric == null) throw new IllegalArgumentException("Valitse, mitä kilpailussa mitataan");
        String d = description == null || description.isBlank() ? null : description.trim();
        if (d != null && d.length() > 2000) throw new IllegalArgumentException("Kuvaus on liian pitkä (enintään 2000 merkkiä)");
        String sp = species == null || species.isBlank() ? null : species.trim();
        if (sp != null && sp.length() > 80) throw new IllegalArgumentException("Lajin nimi on liian pitkä");
        if (metric != EventMetric.COUNT && (countedFish < 1 || countedFish > 10)) {
            throw new IllegalArgumentException("Laskettavien kalojen määrän on oltava 1-10");
        }
        return new Valid(n, d, sp, metric == EventMetric.COUNT ? 1 : countedFish);
    }

    // ---- haku ----

    public List<CompetitionEvent> all() {
        return events.findAllByOrderByStartsAtDesc();
    }

    public Optional<CompetitionEvent> find(Long id) {
        return id == null ? Optional.empty() : events.findById(id);
    }

    private CompetitionEvent require(Long id) {
        return find(id).orElseThrow(() -> new IllegalArgumentException("Kilpailua ei löytynyt"));
    }

    // ---- liittyminen ----

    public boolean isParticipant(CompetitionEvent event, AppUser user) {
        return participants.existsByEventAndUser(event, user);
    }

    public long participantCount(CompetitionEvent event) {
        return participants.countByEvent(event);
    }

    @Transactional
    public void join(Long eventId, AppUser user) {
        CompetitionEvent event = require(eventId);
        if (event.status() == EventStatus.ENDED) throw new IllegalArgumentException("Kilpailu on päättynyt");
        if (!participants.existsByEventAndUser(event, user)) participants.save(new EventParticipant(event, user));
        broadcaster.broadcast(eventId);
    }

    @Transactional
    public void leave(Long eventId, AppUser user) {
        CompetitionEvent event = require(eventId);
        if (event.status() == EventStatus.ENDED) {
            throw new IllegalArgumentException("Päättyneestä kilpailusta ei voi poistua, koska tulokset ovat valmiit");
        }
        participants.deleteByEventAndUser(event, user);
        broadcaster.broadcast(eventId);
    }

    // ---- tulostaulu ----

    /** Kilpailuun kelpaavat saaliit (ennen ylläpitäjän poistoja). */
    private List<CatchPost> eligible(CompetitionEvent event, List<AppUser> members) {
        if (members.isEmpty()) return List.of();
        return posts.findEventCandidates(members, event.getStartsAt(), event.getEndsAt()).stream()
                .filter(event::matchesSpecies)
                .filter(p -> event.getMetric().valueOf(p) != null)
                .toList();
    }

    public Leaderboard leaderboard(Long eventId) {
        CompetitionEvent event = require(eventId);
        List<AppUser> members = participants.findByEvent(event).stream().map(EventParticipant::getUser).toList();
        Map<Long, EventFishReview> byPost = reviews.findByEvent(event).stream()
                .collect(Collectors.toMap(r -> r.getPost().getId(), r -> r, (a, b) -> a));

        List<CatchPost> eligible = eligible(event, members);
        List<EventFishReview> rejected = eligible.stream().map(p -> byPost.get(p.getId()))
                .filter(r -> r != null && r.getStatus() == ReviewStatus.REJECTED).toList();

        Map<Long, List<CatchPost>> perUser = new LinkedHashMap<>();
        for (CatchPost p : eligible) {
            EventFishReview r = byPost.get(p.getId());
            if (r != null && r.getStatus() == ReviewStatus.REJECTED) continue;
            perUser.computeIfAbsent(p.getAuthor().getId(), k -> new ArrayList<>()).add(p);
        }

        EventMetric metric = event.getMetric();
        List<Standing> unranked = new ArrayList<>();
        for (List<CatchPost> fish : perUser.values()) {
            List<CatchPost> counted;
            double score;
            if (metric == EventMetric.COUNT) {
                counted = fish;
                score = fish.size();
            } else {
                counted = fish.stream()
                        .sorted(Comparator.comparingDouble((CatchPost p) -> metric.valueOf(p)).reversed()
                                .thenComparing(CatchPost::getCaughtAt))
                        .limit(event.getCountedFish()).toList();
                score = counted.stream().mapToDouble(metric::valueOf).sum();
            }
            List<CountedFish> detail = counted.stream()
                    .map(p -> new CountedFish(p, metric.valueOf(p), byPost.get(p.getId()))).toList();
            unranked.add(new Standing(0, fish.get(0).getAuthor(), score, detail));
        }

        // Tasatilanteessa voittaa se, joka sai tuloksensa ensin (viimeisin laskettu kala on aiemmin pyydetty)
        Comparator<Standing> order = Comparator.comparingDouble(Standing::score).reversed()
                .thenComparing(s -> s.fish().stream().map(f -> f.post().getCaughtAt()).max(Comparator.naturalOrder())
                        .orElse(Instant.MAX))
                .thenComparing(s -> s.user().getDisplayName());
        List<Standing> sorted = unranked.stream().sorted(order).toList();
        List<Standing> ranked = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            Standing s = sorted.get(i);
            ranked.add(new Standing(i + 1, s.user(), s.score(), s.fish()));
        }
        return new Leaderboard(event, Collections.unmodifiableList(ranked), rejected, members.size());
    }

    // ---- ylläpitäjän tarkistukset ----

    /** Kysyy, onko saalis oikea (lasketaan edelleen mukaan, mutta merkitään) tai poistaa sen kilpailusta. */
    @Transactional
    public void review(Long eventId, Long postId, AppUser actor, ReviewStatus status, String note) {
        requireAdmin(actor);
        CompetitionEvent event = require(eventId);
        CatchPost post = reviewable(event, postId);
        String clean = note == null || note.isBlank() ? null : note.strip();
        if (clean != null && clean.length() > 500) throw new IllegalArgumentException("Huomautus on liian pitkä");

        EventFishReview review = reviews.findByEventAndPost(event, post).orElse(null);
        if (review == null) reviews.save(new EventFishReview(event, post, status, clean, actor));
        else {
            review.set(status, clean, actor);
            reviews.save(review);
        }
        String who = post.getAuthor().getDisplayName();
        String fish = describe(event, post);
        String text = status == ReviewStatus.QUESTIONED
                ? "❓ " + who + ": onko saalis oikea (" + fish + ")?" + (clean == null ? "" : " " + clean)
                : "🚫 Saalis poistettiin kilpailusta: " + who + ", " + fish + "." + (clean == null ? "" : " " + clean);
        addMessage(event, actor, text);
        broadcaster.broadcast(eventId);
    }

    /** Poistaa merkinnän: kala lasketaan taas normaalisti mukaan. */
    @Transactional
    public void clearReview(Long eventId, Long postId, AppUser actor) {
        requireAdmin(actor);
        CompetitionEvent event = require(eventId);
        CatchPost post = posts.findById(postId).orElseThrow(() -> new IllegalArgumentException("Saalista ei löytynyt"));
        Optional<EventFishReview> review = reviews.findByEventAndPost(event, post);
        if (review.isEmpty()) return;
        boolean wasRejected = review.get().getStatus() == ReviewStatus.REJECTED;
        reviews.delete(review.get());
        addMessage(event, actor, (wasRejected ? "↩️ Saalis palautettiin kilpailuun: " : "✅ Saalis hyväksyttiin: ")
                + post.getAuthor().getDisplayName() + ", " + describe(event, post) + ".");
        broadcaster.broadcast(eventId);
    }

    private CatchPost reviewable(CompetitionEvent event, Long postId) {
        CatchPost post = posts.findById(postId).orElseThrow(() -> new IllegalArgumentException("Saalista ei löytynyt"));
        List<AppUser> members = participants.findByEvent(event).stream().map(EventParticipant::getUser).toList();
        boolean ok = eligible(event, members).stream().anyMatch(p -> p.getId().equals(postId));
        if (!ok) throw new IllegalArgumentException("Saalis ei kuulu tämän kilpailun tuloksiin");
        return post;
    }

    // ---- keskustelu (kaikki kirjautuneet voivat kirjoittaa) ----

    /** Uusimmat viestit vanhimmasta uusimpaan. */
    public List<EventMessage> messages(Long eventId) {
        List<EventMessage> list = new ArrayList<>(messages.findTop100ByEventOrderBySentAtDesc(require(eventId)));
        Collections.reverse(list);
        return list;
    }

    @Transactional
    public void postMessage(Long eventId, AppUser sender, String text) {
        String clean = text == null ? "" : text.strip();
        if (clean.isEmpty()) return;
        if (clean.length() > 1000) throw new IllegalArgumentException("Viesti on liian pitkä (enintään 1000 merkkiä)");
        addMessage(require(eventId), sender, clean);
        broadcaster.broadcast(eventId);
    }

    private void addMessage(CompetitionEvent event, AppUser sender, String text) {
        String t = text.length() > 1000 ? text.substring(0, 1000) : text;
        messages.save(new EventMessage(event, sender, t));
    }

    // ---- muotoilu ----

    /** "Hauki 5,4 kg" tai pelkkä laji. */
    public String describe(CompetitionEvent event, CatchPost post) {
        Double v = event.getMetric() == EventMetric.COUNT ? null : event.getMetric().valueOf(post);
        return v == null ? post.getSpecies() : post.getSpecies() + " " + format(v, event.getMetric());
    }

    public static String format(double value, EventMetric metric) {
        String n = new BigDecimal(String.format(Locale.ROOT, "%.2f", value)).stripTrailingZeros().toPlainString();
        return n.replace('.', ',') + " " + metric.unit();
    }

    // ---- siivous muiden toimintojen yhteydessä ----

    /** Poistaa käyttäjän osallistumiset ja viestit (tilin poisto). */
    @Transactional
    void removeUser(AppUser user) {
        participants.deleteByUser(user);
        messages.deleteBySender(user);
    }

    @Transactional
    void removePostReviews(CatchPost post) {
        reviews.deleteByPost(post);
    }
}
