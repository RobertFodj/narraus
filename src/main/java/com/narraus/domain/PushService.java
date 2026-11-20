package com.narraus.domain;

import com.vaadin.flow.server.webpush.WebPush;
import com.vaadin.flow.server.webpush.WebPushSubscription;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Web push -asetukset ja käyttäjien laitekohtaiset tilaukset. */
@Service
public class PushService {

    private final String publicKey;
    private final String privateKey;
    private final String subject;
    private final PushSubscriptionRepository subscriptions;
    private WebPush webPush;

    PushService(@Value("${narraus.push.public-key:}") String publicKey,
                @Value("${narraus.push.private-key:}") String privateKey,
                @Value("${narraus.push.subject:}") String subject,
                PushSubscriptionRepository subscriptions) {
        this.publicKey = publicKey == null ? "" : publicKey.trim();
        this.privateKey = privateKey == null ? "" : privateKey.trim();
        this.subject = subject;
        this.subscriptions = subscriptions;
    }

    /** Push on käytössä vain, jos VAPID-avainpari on asetettu. */
    public boolean isEnabled() {
        return !publicKey.isBlank() && !privateKey.isBlank();
    }

    /** Selaimelle annettava julkinen avain. */
    public String publicKey() {
        return publicKey;
    }

    /** Vaadinin WebPush-olio tilausten tekemiseen selaimessa ja viestien lähettämiseen. */
    public synchronized WebPush webPush() {
        if (!isEnabled()) throw new IllegalStateException("Push-ilmoitukset eivät ole käytössä");
        if (webPush == null) webPush = new WebPush(publicKey, privateKey, subject);
        return webPush;
    }

    /** Tallentaa laitteen tilauksen käyttäjälle. Sama laite (endpoint) siirtyy tarvittaessa uudelle käyttäjälle. */
    @Transactional
    public void subscribe(AppUser user, WebPushSubscription subscription) {
        if (user == null || subscription == null || subscription.endpoint() == null || subscription.keys() == null) {
            throw new IllegalArgumentException("Virheellinen tilaus");
        }
        String p256dh = subscription.keys().p256dh();
        String auth = subscription.keys().auth();
        subscriptions.findByEndpoint(subscription.endpoint()).ifPresentOrElse(
                existing -> existing.reassign(user, p256dh, auth),
                () -> subscriptions.save(new PushSubscriptionEntity(user, subscription.endpoint(), p256dh, auth)));
    }

    /** Poistaa käyttäjän oman laitteen tilauksen. Toisen käyttäjän tilaukseen ei voi koskea. */
    @Transactional
    public void unsubscribe(AppUser user, String endpoint) {
        subscriptions.deleteByUserAndEndpoint(user, endpoint);
    }

    /**
     * Järjestelmä poistaa tilauksen, jonka push-palvelu kertoo poistuneeksi. Oma transaktio, koska kutsu tulee
     * usein ilmoitustapahtuman commitin jälkeen, jolloin mukaan liittyminen ei tallentaisi poistoa.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void removeEndpoint(String endpoint) {
        subscriptions.deleteByEndpoint(endpoint);
    }

    public boolean isSubscribed(AppUser user, String endpoint) {
        return subscriptions.existsByUserAndEndpoint(user, endpoint);
    }

    public long deviceCount(AppUser user) {
        return subscriptions.countByUser(user);
    }
}
