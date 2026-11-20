package com.narraus.domain;

import com.vaadin.flow.server.webpush.WebPushException;
import com.vaadin.flow.server.webpush.WebPushKeys;
import com.vaadin.flow.server.webpush.WebPushMessage;
import com.vaadin.flow.server.webpush.WebPushSubscription;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.stereotype.Component;

/** Oikea lähetys Vaadinin WebPush-luokalla (VAPID). */
@Component
class VapidPushSender implements PushSender {

    private static final Logger LOG = Logger.getLogger(VapidPushSender.class.getName());

    /** Ilmoituksen lisäasetukset (body ja icon), joiden muoto on selaimen showNotification-asetukset. */
    record Options(String body, String icon) implements java.io.Serializable {}

    private final PushService push;

    VapidPushSender(PushService push) {
        this.push = push;
    }

    @Override
    public Result send(String endpoint, String p256dh, String auth, String title, String body, String icon) {
        if (!push.isEnabled()) return Result.FAILED;
        try {
            WebPushMessage message = icon == null
                    ? new WebPushMessage(title, body)
                    : new WebPushMessage(title, new Options(body, icon));
            push.webPush().sendNotification(new WebPushSubscription(endpoint, new WebPushKeys(p256dh, auth)),
                    message);
            return Result.OK;
        } catch (WebPushException e) {
            if (isGone(e)) return Result.GONE;
            LOG.log(Level.WARNING, "Push-viestin lähetys epäonnistui", e);
            return Result.FAILED;
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Push-viestin lähetys epäonnistui", e);
            return Result.FAILED;
        }
    }

    /** Push-palvelu vastaa 404/410, kun selain on peruuttanut tilauksen. */
    static boolean isGone(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String m = t.getMessage();
            if (m != null && (m.contains("410") || m.contains("404") || m.toLowerCase().contains("gone"))) return true;
        }
        return false;
    }
}
