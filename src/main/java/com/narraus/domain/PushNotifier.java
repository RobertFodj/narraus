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
    /** Narrauksen logo (PWA-kuvake), joka näkyy Narraus-viestien ilmoituksessa. */
    static final String LOGO_ICON = "/icons/icon-192x192.png";

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
        deliver(e.recipientIds(), title, body, null);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onPostPublished(PushEvents.PostPublished e) {
        deliver(e.recipientIds(), "Uusi saalis", e.authorName() + ": " + e.species(), null);
    }

    /** Narraus-viesti: lähettäjänä "Narraus" ja kuvakkeena Narrauksen logo. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onSystemMessage(PushEvents.SystemMessage e) {
        deliver(e.recipientIds(), SystemMessageService.SENDER_NAME, preview(e.text()), LOGO_ICON);
    }

    private void deliver(Set<Long> recipientIds, String title, String body, String icon) {
        if (!push.isEnabled() || recipientIds.isEmpty()) return;
        List<PushSubscriptionEntity> targets = subscriptions.findByUserIdIn(recipientIds);
        for (PushSubscriptionEntity t : targets) {
            String endpoint = t.getEndpoint(), p256dh = t.getP256dh(), auth = t.getAuth();
            dispatcher.dispatch(() -> {
                if (sender.send(endpoint, p256dh, auth, title, body, icon) == PushSender.Result.GONE) {
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
