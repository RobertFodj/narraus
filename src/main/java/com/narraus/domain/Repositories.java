package com.narraus.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface Repositories {}

interface UserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsername(String username);
    boolean existsByUsernameIgnoreCase(String username);
    List<AppUser> findByDisplayNameContainingIgnoreCaseOrderByDisplayName(String name, Pageable page);
    List<AppUser> findByDisplayNameContainingIgnoreCaseAndIdNotOrderByDisplayName(String name, Long excludedId, Pageable page);
    long countByDisplayNameContainingIgnoreCaseAndIdNot(String name, Long excludedId);
}

interface PostRepository extends JpaRepository<CatchPost, Long> {
    /** Kilpailuun kelpaavat ehdokkaat: osallistujien kuvalliset saaliit kilpailun aikana. Lajin ja mitan tarkistus tehdään koodissa. */
    @Query("select p from CatchPost p where p.author in :authors and p.caughtAt >= :start and p.caughtAt <= :end"
            + " and p.photoFile is not null order by p.caughtAt")
    List<CatchPost> findEventCandidates(@Param("authors") Collection<AppUser> authors,
                                        @Param("start") java.time.Instant start, @Param("end") java.time.Instant end);

    List<CatchPost> findAllByOrderByCaughtAtDesc(Pageable page);
    List<CatchPost> findByAuthorInOrderByCaughtAtDesc(Collection<AppUser> authors, Pageable page);
    List<CatchPost> findByAuthorOrderByCaughtAtDesc(AppUser author, Pageable page);
    long countByAuthor(AppUser author);
    List<CatchPost> findByAuthor(AppUser author);
    long countByAuthorNotAndCreatedAtAfter(AppUser author, Instant after);
    List<CatchPost> findByLatitudeIsNotNullAndLongitudeIsNotNullOrderByCaughtAtDesc(Pageable page);
}

interface LikeRepository extends JpaRepository<PostLike, Long> {
    long countByPost(CatchPost post);
    boolean existsByPostAndUser(CatchPost post, AppUser user);
    void deleteByPostAndUser(CatchPost post, AppUser user);
    void deleteByPost(CatchPost post);
    void deleteByUser(AppUser user);
}

interface CommentRepository extends JpaRepository<PostComment, Long> {
    List<PostComment> findByPostOrderByCreatedAtAsc(CatchPost post);
    long countByPost(CatchPost post);
    void deleteByPost(CatchPost post);
    void deleteByAuthor(AppUser author);
}

interface FollowRepository extends JpaRepository<Follow, Long> {
    List<Follow> findByFollower(AppUser follower);
    List<Follow> findByFollowed(AppUser followed);
    boolean existsByFollowerAndFollowed(AppUser follower, AppUser followed);
    void deleteByFollowerAndFollowed(AppUser follower, AppUser followed);
    long countByFollowed(AppUser followed);
    long countByFollower(AppUser follower);
    void deleteByFollower(AppUser follower);
    void deleteByFollowed(AppUser followed);
}

interface ConversationRepository extends JpaRepository<Conversation, Long> {
    @Query("select distinct c from Conversation c join c.participants p where p = :user order by c.lastActivity desc")
    List<Conversation> findForUser(@Param("user") AppUser user);

    @Query("select c from Conversation c where c.groupChat = false and :a member of c.participants and :b member of c.participants")
    List<Conversation> findDirect(@Param("a") AppUser a, @Param("b") AppUser b);
}

interface MessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findTop200ByConversationOrderBySentAtDesc(Conversation conversation);
    void deleteBySenderAndConversation(AppUser sender, Conversation conversation);
    void deleteByConversation(Conversation conversation);
    List<ChatMessage> findTop20ByConversationAndSenderOrderBySentAtDesc(Conversation conversation, AppUser sender);
    long countByConversationAndSenderNotAndSentAtAfter(Conversation conversation, AppUser sender, Instant after);
}

interface ConversationReadRepository extends JpaRepository<ConversationRead, Long> {
    Optional<ConversationRead> findByConversationAndUser(Conversation conversation, AppUser user);
    void deleteByUser(AppUser user);
    void deleteByConversation(Conversation conversation);
}

interface PushSubscriptionRepository extends JpaRepository<PushSubscriptionEntity, Long> {
    Optional<PushSubscriptionEntity> findByEndpoint(String endpoint);
    List<PushSubscriptionEntity> findByUserIdIn(Collection<Long> userIds);
    long countByUser(AppUser user);
    boolean existsByUserAndEndpoint(AppUser user, String endpoint);
    void deleteByUserAndEndpoint(AppUser user, String endpoint);
    void deleteByEndpoint(String endpoint);
    void deleteByUser(AppUser user);
}

interface CompetitionEventRepository extends JpaRepository<CompetitionEvent, Long> {
    List<CompetitionEvent> findAllByOrderByStartsAtDesc();
}

interface EventParticipantRepository extends JpaRepository<EventParticipant, Long> {
    boolean existsByEventAndUser(CompetitionEvent event, AppUser user);
    long countByEvent(CompetitionEvent event);
    List<EventParticipant> findByEvent(CompetitionEvent event);
    List<EventParticipant> findByUser(AppUser user);
    void deleteByEventAndUser(CompetitionEvent event, AppUser user);
    void deleteByEvent(CompetitionEvent event);
    void deleteByUser(AppUser user);
}

interface EventFishReviewRepository extends JpaRepository<EventFishReview, Long> {
    List<EventFishReview> findByEvent(CompetitionEvent event);
    Optional<EventFishReview> findByEventAndPost(CompetitionEvent event, CatchPost post);
    void deleteByEvent(CompetitionEvent event);
    void deleteByPost(CatchPost post);
}

interface EventMessageRepository extends JpaRepository<EventMessage, Long> {
    List<EventMessage> findTop100ByEventOrderBySentAtDesc(CompetitionEvent event);
    void deleteByEvent(CompetitionEvent event);
    void deleteBySender(AppUser sender);
}

interface SystemMessageRepository extends JpaRepository<SystemMessage, Long> {
    List<SystemMessage> findTop200ByRecipientOrderByCreatedAtDesc(AppUser recipient);
    long countByRecipient(AppUser recipient);
    long countByRecipientAndReadAtIsNull(AppUser recipient);
    void deleteByRecipient(AppUser recipient);

    @Modifying
    @Query("update SystemMessage m set m.readAt = :now where m.recipient = :user and m.readAt is null")
    int markAllRead(@Param("user") AppUser user, @Param("now") Instant now);
}

interface NotificationRepository extends JpaRepository<AppNotification, Long> {
    List<AppNotification> findTop50ByRecipientOrderByCreatedAtDesc(AppUser recipient);
    long countByRecipientAndReadAtIsNull(AppUser recipient);
    boolean existsByRecipientAndActorAndTypeAndTargetId(AppUser recipient, AppUser actor, NotificationType type, Long targetId);
    boolean existsByRecipientAndActorAndTypeAndCreatedAtAfter(AppUser recipient, AppUser actor, NotificationType type, Instant after);
    void deleteByRecipient(AppUser recipient);
    void deleteByActor(AppUser actor);

    @Modifying
    @Query("update AppNotification n set n.readAt = :now where n.recipient = :user and n.readAt is null")
    int markAllRead(@Param("user") AppUser user, @Param("now") Instant now);
}
