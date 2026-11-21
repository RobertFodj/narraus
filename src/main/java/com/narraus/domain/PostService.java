package com.narraus.domain;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class PostService {

    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", ".jpg", "image/png", ".png", "image/gif", ".gif", "image/webp", ".webp");

    /** Tallennettavan kuvan pisin sivu ja JPEG-laatu; pienempi tiedosto latautuu nopeammin. */
    private static final int MAX_EDGE = 1600;
    private static final float QUALITY = 0.8f;
    /** Alle tämän kokoista, jo riittävän pientä kuvaa ei koodata uudelleen (säästää laadun). */
    private static final int KEEP_AS_IS_BYTES = 300_000;
    /** Korttien esikatselukuva. */
    private static final int THUMB_EDGE = 480;
    private static final float THUMB_QUALITY = 0.75f;

    private final PostRepository posts;
    private final LikeRepository likes;
    private final CommentRepository comments;
    private final EventFishReviewRepository eventReviews;
    private final FollowRepository follows;
    private final FeedBroadcaster feedBroadcaster;
    private final ApplicationEventPublisher events;
    private final Path uploadDir;
    private final AdminPolicy adminPolicy;
    private final NotificationService notifications;

    PostService(PostRepository posts, LikeRepository likes, CommentRepository comments, EventFishReviewRepository eventReviews, FollowRepository follows,
                FeedBroadcaster feedBroadcaster, ApplicationEventPublisher events, AdminPolicy adminPolicy,
                NotificationService notifications,
                @Value("${narraus.upload-dir}") String uploadDir) throws IOException {
        this.adminPolicy = adminPolicy;
        this.notifications = notifications;
        this.events = events;
        this.feedBroadcaster = feedBroadcaster;
        this.posts = posts;
        this.likes = likes;
        this.comments = comments;
        this.eventReviews = eventReviews;
        this.follows = follows;
        this.uploadDir = Path.of(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(this.uploadDir);
    }

    @Transactional
    public CatchPost create(AppUser author, String species, Double weightKg, Double lengthCm, Double girthCm,
                            String location, String description, byte[] photo, String contentType) {
        return create(author, species, weightKg, lengthCm, girthCm, location, description, photo, contentType,
                null, null);
    }

    @Transactional
    public CatchPost create(AppUser author, String species, Double weightKg, Double lengthCm, Double girthCm,
                            String location, String description, byte[] photo, String contentType,
                            Double latitude, Double longitude) {
        return create(author, species, weightKg, lengthCm, girthCm, location, description, photo, contentType,
                latitude, longitude, null);
    }

    /** caughtAt = milloin kala on pyydetty; null tarkoittaa nykyhetkeä. Aika ei voi olla tulevaisuudessa. */
    @Transactional
    public CatchPost create(AppUser author, String species, Double weightKg, Double lengthCm, Double girthCm,
                            String location, String description, byte[] photo, String contentType,
                            Double latitude, Double longitude, Instant caughtAt) {
        Instant when = caughtAt == null ? null : requireNotFuture(caughtAt);
        String file = storePhoto(photo, contentType);
        validateCoordinates(latitude, longitude);
        CatchPost post = new CatchPost(author, requireSpecies(species), weightKg, lengthCm, girthCm,
                blankToNull(location), blankToNull(description), file);
        post.setCoordinates(latitude, longitude);
        if (when != null) post.setCaughtAt(when);
        CatchPost saved = posts.save(post);
        feedBroadcaster.broadcast(saved.getId());
        // Ilmoitus tekijän seuraajille (ei kaikille, ettei ilmoituksia tule liikaa)
        Set<Long> followers = follows.findByFollowed(author).stream().map(f -> f.getFollower().getId())
                .collect(Collectors.toSet());
        events.publishEvent(new PushEvents.PostPublished(saved.getId(), author.getId(), author.getDisplayName(),
                saved.getSpecies(), followers));
        return saved;
    }

    /**
     * Muokkaa omaa julkaisua. Vain tekijä saa muokata (SecurityException muille).
     * Uusi kuva korvaa vanhan; removePhoto poistaa kuvan ilman korvaajaa; muuten kuva säilyy.
     */
    @Transactional
    public CatchPost update(Long postId, AppUser requester, String species, Double weightKg, Double lengthCm,
                            Double girthCm, String location, String description, byte[] newPhoto,
                            String contentType, boolean removePhoto, Double latitude, Double longitude) {
        return update(postId, requester, species, weightKg, lengthCm, girthCm, location, description, newPhoto,
                contentType, removePhoto, latitude, longitude, null);
    }

    /** caughtAt: uusi pyyntiaika, tai null jos aika säilytetään ennallaan. Aika ei voi olla tulevaisuudessa. */
    @Transactional
    public CatchPost update(Long postId, AppUser requester, String species, Double weightKg, Double lengthCm,
                            Double girthCm, String location, String description, byte[] newPhoto,
                            String contentType, boolean removePhoto, Double latitude, Double longitude,
                            Instant caughtAt) {
        CatchPost post = ownedBy(postId, requester);
        String newSpecies = requireSpecies(species);
        validateCoordinates(latitude, longitude);
        Instant when = caughtAt == null ? null : requireNotFuture(caughtAt);

        String oldFile = post.getPhotoFile();
        String file = oldFile;
        if (newPhoto != null && newPhoto.length > 0) {
            file = storePhoto(newPhoto, contentType);
        } else if (removePhoto) {
            file = null;
        }

        post.edit(newSpecies, weightKg, lengthCm, girthCm, blankToNull(location), blankToNull(description));
        post.setCoordinates(latitude, longitude);
        if (when != null) post.setCaughtAt(when);
        post.setPhotoFile(file);
        CatchPost saved = posts.save(post);

        if (oldFile != null && !Objects.equals(oldFile, file)) afterCommit(() -> deletePhotoQuietly(oldFile));
        feedBroadcaster.broadcast(saved.getId());
        return saved;
    }

    /** Poistaa julkaisun tykkäyksineen ja kuvineen. Vain tekijä tai ylläpitäjä saa poistaa (SecurityException muille). */
    @Transactional
    public void delete(Long postId, AppUser requester) {
        CatchPost post = ownedBy(postId, requester);
        String file = post.getPhotoFile();
        likes.deleteByPost(post);
        comments.deleteByPost(post);
        eventReviews.deleteByPost(post);
        posts.delete(post);
        if (file != null) afterCommit(() -> deletePhotoQuietly(file));
        feedBroadcaster.broadcast(postId);
    }

    private CatchPost ownedBy(Long postId, AppUser requester) {
        CatchPost post = posts.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Julkaisua ei löytynyt"));
        // Tekijä hallitsee omaa julkaisuaan; ylläpitäjä saa muokata ja poistaa kenen tahansa julkaisun
        if (requester == null || !(post.getAuthor().equals(requester) || adminPolicy.isAdmin(requester))) {
            throw new SecurityException("Ei oikeutta");
        }
        return post;
    }

    private static String requireSpecies(String species) {
        if (species == null || species.isBlank()) throw new IllegalArgumentException("Kalalaji vaaditaan");
        return species.trim();
    }

    private static void validateCoordinates(Double latitude, Double longitude) {
        if (latitude != null && (latitude < -90 || latitude > 90)
                || longitude != null && (longitude < -180 || longitude > 180)) {
            throw new IllegalArgumentException("Sijainti ei kelpaa");
        }
    }

    /** Tallentaa kuvan levylle ja palauttaa tiedostonimen; null, jos kuvaa ei ole. */
    private String storePhoto(byte[] photo, String contentType) {
        if (photo == null || photo.length == 0) return null;
        String ext = EXTENSIONS.get(contentType);
        if (ext == null) throw new IllegalArgumentException("Kuvan tyyppi ei kelpaa (jpg, png, gif tai webp)");
        byte[] data = photo;
        // Animoitua gif-kuvaa ja kuvaa, jota ei voi lukea (esim. webp), ei muunneta. Muut pienennetään.
        if (!".gif".equals(ext)) {
            int edge = PhotoOptimizer.longEdge(photo);
            boolean large = edge > MAX_EDGE || photo.length > KEEP_AS_IS_BYTES;
            if (large) {
                var shrunk = PhotoOptimizer.shrink(photo, MAX_EDGE, QUALITY);
                if (shrunk.isPresent() && shrunk.get().length < photo.length) {
                    data = shrunk.get();
                    ext = ".jpg";
                }
            }
        }
        String file = UUID.randomUUID() + ext;
        try {
            Files.write(uploadDir.resolve(file), data);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return file;
    }

    private static String thumbName(String file) {
        int dot = file.lastIndexOf('.');
        return (dot < 0 ? file : file.substring(0, dot)) + "-thumb.jpg";
    }

    private void deletePhotoQuietly(String file) {
        Path p = uploadDir.resolve(file).normalize();
        if (!p.startsWith(uploadDir)) return;
        try {
            Files.deleteIfExists(p);
            Files.deleteIfExists(uploadDir.resolve(thumbName(file)).normalize());
        } catch (IOException ignored) {
            // levylle jäänyt orpo kuva ei estä toimintoa
        }
    }

    /** Suorittaa toiminnon vasta onnistuneen commitin jälkeen (tai heti, jos transaktiota ei ole). */
    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    /** Julkaisu tunnisteen mukaan (tyhjä, jos sitä ei ole). */
    public java.util.Optional<CatchPost> find(Long id) {
        return id == null ? java.util.Optional.empty() : posts.findById(id);
    }

    public List<CatchPost> feed(AppUser me, boolean onlyFollowed) {
        return feedPage(me, onlyFollowed, 0, 50);
    }

    /** Syötteen sivu (uusimmasta vanhimpaan); käyttöliittymä lataa lisää vasta kun käyttäjä vierittää sinne. */
    public List<CatchPost> feedPage(AppUser me, boolean onlyFollowed, int pageIndex, int size) {
        var page = PageRequest.of(pageIndex, size);
        if (!onlyFollowed) return posts.findAllByOrderByCaughtAtDesc(page);
        List<AppUser> authors = new ArrayList<>(follows.findByFollower(me).stream().map(Follow::getFollowed).toList());
        authors.add(me);
        return posts.findByAuthorInOrderByCaughtAtDesc(authors, page);
    }

    /** Yhden käyttäjän julkaisut uusimmasta vanhimpaan (enintään 50). */
    public List<CatchPost> byAuthor(AppUser author) {
        return posts.findByAuthorOrderByCaughtAtDesc(author, PageRequest.of(0, 50));
    }

    /** Kaikki julkaisut, joilla on sijainti (kartalle), uusimmat ensin (enintään 500). */
    public List<CatchPost> withLocation() {
        return posts.findByLatitudeIsNotNullAndLongitudeIsNotNullOrderByCaughtAtDesc(PageRequest.of(0, 500));
    }

    public long postCount(AppUser author) {
        return posts.countByAuthor(author);
    }

    /** Palauttaa kuvan; tiedostonimi on aina palvelimen itse generoima. */
    /**
     * Pieni esikatselukuva korttia varten. Tehdään ensimmäisellä pyynnöllä ja tallennetaan levylle, joten myös
     * ennen tätä ladatuille kuville syntyy esikatselu. Jos kuvaa ei voi pienentää, palautetaan alkuperäinen.
     */
    public InputStream openThumbnail(String file) {
        Path original = uploadDir.resolve(file).normalize();
        Path thumb = uploadDir.resolve(thumbName(file)).normalize();
        if (!original.startsWith(uploadDir) || !thumb.startsWith(uploadDir)) throw new IllegalArgumentException();
        try {
            if (!Files.exists(thumb)) {
                var small = PhotoOptimizer.shrink(Files.readAllBytes(original), THUMB_EDGE, THUMB_QUALITY);
                if (small.isEmpty()) return Files.newInputStream(original);
                Files.write(thumb, small.get());
            }
            return Files.newInputStream(thumb);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public InputStream openPhoto(String file) {
        Path p = uploadDir.resolve(file).normalize();
        if (!p.startsWith(uploadDir)) throw new IllegalArgumentException();
        try {
            return Files.newInputStream(p);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public long likeCount(CatchPost post) {
        return likes.countByPost(post);
    }

    public boolean isLiked(CatchPost post, AppUser user) {
        return likes.existsByPostAndUser(post, user);
    }

    @Transactional
    public void toggleLike(CatchPost post, AppUser user) {
        if (likes.existsByPostAndUser(post, user)) likes.deleteByPostAndUser(post, user);
        else {
            likes.save(new PostLike(post, user));
            notifications.newLike(post.getAuthor(), user, post); // ilmoitus tekijälle
        }
    }

    private static Instant requireNotFuture(Instant t) {
        if (t.isAfter(Instant.now().plus(Duration.ofMinutes(5)))) {
            throw new IllegalArgumentException("Saaliin aika ei voi olla tulevaisuudessa");
        }
        return t;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    // --- kommentit ---

    public List<PostComment> comments(CatchPost post) {
        return comments.findByPostOrderByCreatedAtAsc(post);
    }

    public long commentCount(CatchPost post) {
        return comments.countByPost(post);
    }

    /** Lisää kommentin saaliiseen. Kuka tahansa kirjautunut käyttäjä saa kommentoida. */
    @Transactional
    public PostComment addComment(Long postId, AppUser author, String text) {
        String clean = text == null ? "" : text.strip();
        if (clean.isEmpty()) throw new IllegalArgumentException("Kommentti ei voi olla tyhjä");
        if (clean.length() > 1000) throw new IllegalArgumentException("Kommentti on liian pitkä (enintään 1000 merkkiä)");
        CatchPost post = posts.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Julkaisua ei löytynyt"));
        return comments.save(new PostComment(post, author, clean));
    }
}
