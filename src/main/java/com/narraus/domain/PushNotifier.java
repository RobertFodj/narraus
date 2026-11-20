package com.narraus.domain;

import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Lähettää push-ilmoitukset uusista viesteistä ja seuratun kalastajan julkaisuista. */
@Component
class PushNotifier {

    private static final int PREVIEW_LENGTH = 80;

    private final PushService push;
    private final PushSender sender;
    private final PushSubscriptionRepository subscriptions;
    private final PushDispatcher dispatcher;

    PushNotifier(PushService push, PushSender sender, PushSubscriptionRepository subscriptions,
                 PushDispatcher dispatcher) {
        this.push = push;
        this.sender = sender;
        this.subscriptions = subscriptions;
        this.dispatcher = dispatcher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onMessageSent(PushEvents.MessageSent e) {
        boolean group = e.groupName() != null;
        String title = group ? e.groupName() : e.senderName();
        String preview = preview(e.text());
        String body = group ? e.senderName() + ": " + preview : preview;
        deliver(e.recipientIds(), title, body);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onPostPublished(PushEvents.PostPublished e) {
        deliver(e.recipientIds(), "Uusi saalis", e.authorName() + ": " + e.species());
    }

    private void deliver(Set<Long> recipientIds, String title, String body) {
        if (!push.isEnabled() || recipientIds.isEmpty()) return;
        List<PushSubscriptionEntity> targets = subscriptions.findByUserIdIn(recipientIds);
        for (PushSubscriptionEntity t : targets) {
            String endpoint = t.getEndpoint(), p256dh = t.getP256dh(), auth = t.getAuth();
            dispatcher.dispatch(() -> {
                if (sender.send(endpoint, p256dh, auth, title, body) == PushSender.Result.GONE) {
                    push.removeEndpoint(endpoint);
                }
            });
        }
    }

    static String preview(String text) {
        String oneLine = text == null ? "" : text.replaceAll("\\s+", " ").strip();
        return oneLine.length() > PREVIEW_LENGTH ? oneLine.substring(0, PREVIEW_LENGTH) + "…" : oneLine;
    }
}
