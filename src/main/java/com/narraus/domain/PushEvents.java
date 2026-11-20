package com.narraus.domain;

import java.util.Set;

/** Sovelluksen tapahtumat, joista lähetetään push-ilmoitus transaktion commitin jälkeen. */
public final class PushEvents {

    private PushEvents() {}

    /** Keskusteluun lähetettiin viesti. recipientIds ei sisällä lähettäjää. */
    public record MessageSent(Long conversationId, Long senderId, String senderName, String groupName, String text,
                              Set<Long> recipientIds) {}

    /** Julkaistiin uusi saalis. recipientIds on tekijän seuraajat. */
    public record PostPublished(Long postId, Long authorId, String authorName, String species,
                                Set<Long> recipientIds) {}

    /** Ylläpitäjä lähetti Narraus-viestin. Ilmoituksen lähettäjänä on "Narraus" ja kuvakkeena Narrauksen logo. */
    public record SystemMessage(Set<Long> recipientIds, String text) {}
}
