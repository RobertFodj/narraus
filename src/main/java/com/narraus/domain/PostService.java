package com.narraus.domain;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PostService {

    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", ".jpg", "image/png", ".png", "image/gif", ".gif", "image/webp", ".webp");

    private final PostRepository posts;
    private final LikeRepository likes;
    private final FollowRepository follows;
    private final Path uploadDir;

    PostService(PostRepository posts, LikeRepository likes, FollowRepository follows,
                @Value("${narraus.upload-dir}") String uploadDir) throws IOException {
        this.posts = posts;
        this.likes = likes;
        this.follows = follows;
        this.uploadDir = Path.of(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(this.uploadDir);
    }

    @Transactional
    public CatchPost create(AppUser author, String species, Double weightKg, Double lengthCm, Double girthCm,
                            String location, String description, byte[] photo, String contentType) {
        String file = null;
        if (photo != null && photo.length > 0) {
            String ext = EXTENSIONS.get(contentType);
            if (ext == null) throw new IllegalArgumentException("Kuvan tyyppi ei kelpaa (jpg, png, gif tai webp)");
            file = UUID.randomUUID() + ext;
            try {
                Files.write(uploadDir.resolve(file), photo);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return posts.save(new CatchPost(author, species.trim(), weightKg, lengthCm, girthCm,
                blankToNull(location), blankToNull(description), file));
    }

    public List<CatchPost> feed(AppUser me, boolean onlyFollowed) {
        var page = PageRequest.of(0, 50);
        if (!onlyFollowed) return posts.findAllByOrderByCreatedAtDesc(page);
        List<AppUser> authors = new ArrayList<>(follows.findByFollower(me).stream().map(Follow::getFollowed).toList());
        authors.add(me);
        return posts.findByAuthorInOrderByCreatedAtDesc(authors, page);
    }

    /** Palauttaa kuvan; tiedostonimi on aina palvelimen itse generoima. */
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
        else likes.save(new PostLike(post, user));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
