package com.narraus.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface Repositories {}

interface UserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsername(String username);
    boolean existsByUsernameIgnoreCase(String username);
    List<AppUser> findByDisplayNameContainingIgnoreCaseOrUsernameContainingIgnoreCaseOrderByDisplayName(
            String name, String username, Pageable page);
}

interface PostRepository extends JpaRepository<CatchPost, Long> {
    List<CatchPost> findAllByOrderByCreatedAtDesc(Pageable page);
    List<CatchPost> findByAuthorInOrderByCreatedAtDesc(Collection<AppUser> authors, Pageable page);
}

interface LikeRepository extends JpaRepository<PostLike, Long> {
    long countByPost(CatchPost post);
    boolean existsByPostAndUser(CatchPost post, AppUser user);
    void deleteByPostAndUser(CatchPost post, AppUser user);
}

interface FollowRepository extends JpaRepository<Follow, Long> {
    List<Follow> findByFollower(AppUser follower);
    boolean existsByFollowerAndFollowed(AppUser follower, AppUser followed);
    void deleteByFollowerAndFollowed(AppUser follower, AppUser followed);
    long countByFollowed(AppUser followed);
}

interface ConversationRepository extends JpaRepository<Conversation, Long> {
    @Query("select distinct c from Conversation c join c.participants p where p = :user order by c.lastActivity desc")
    List<Conversation> findForUser(@Param("user") AppUser user);

    @Query("select c from Conversation c where c.groupChat = false and :a member of c.participants and :b member of c.participants")
    List<Conversation> findDirect(@Param("a") AppUser a, @Param("b") AppUser b);
}

interface MessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findTop200ByConversationOrderBySentAtDesc(Conversation conversation);
}
