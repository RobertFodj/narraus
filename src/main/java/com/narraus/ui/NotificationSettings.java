package com.narraus.ui;

import com.narraus.domain.AppUser;
import com.narraus.domain.PushService;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.server.webpush.WebPushSubscription;

/** Oman sivun osio, jolla käyttäjä ottaa push-ilmoitukset käyttöön tai pois käytöstä tällä laitteella. */
class NotificationSettings extends VerticalLayout {

    private final PushService push;
    private final CurrentUser currentUser;
    private final Span status = new Span();
    private final Button subscribe = new Button("Ota ilmoitukset käyttöön", VaadinIcon.BELL.create());
    private final Button unsubscribe = new Button("Poista ilmoitukset käytöstä", VaadinIcon.BELL_SLASH.create());

    NotificationSettings(PushService push, CurrentUser currentUser) {
        this.push = push;
        this.currentUser = currentUser;
        setPadding(false);
        setSpacing(false);

        add(new H3("Ilmoitukset"));
        if (!push.isEnabled()) {
            add(new Paragraph("Push-ilmoitukset eivät ole käytössä tällä palvelimella."));
            return;
        }
        add(new Paragraph("Saat ilmoituksen uusista viesteistä ja seuraamiesi kalastajien julkaisuista, vaikka "
                + "sovellus ei ole auki. Ilmoitukset ovat laitekohtaisia. iPhonessa lisää sovellus ensin "
                + "aloitusnäyttöön (Jaa → Lisää Koti-valikkoon)."));

        subscribe.addThemeVariants(ButtonVariant.PRIMARY, ButtonVariant.SMALL);
        unsubscribe.addThemeVariants(ButtonVariant.SMALL);
        subscribe.addClickListener(e -> subscribe.getUI().ifPresent(ui ->
                push.webPush().subscribe(ui, this::onSubscribed)));
        unsubscribe.addClickListener(e -> unsubscribe.getUI().ifPresent(ui ->
                push.webPush().unsubscribe(ui, this::onUnsubscribed)));
        show(false);
        add(status, subscribe, unsubscribe);
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        if (!push.isEnabled()) return;
        UI ui = attachEvent.getUI();
        push.webPush().subscriptionExists(ui, registered -> {
            show(registered);
            // Laite on tilannut ilmoitukset, mutta palvelin ei tiedä sitä (tai tilaus oli toisen käyttäjän)
            if (registered) {
                push.webPush().fetchExistingSubscription(ui, sub -> {
                    if (!push.isSubscribed(currentUser.get(), sub.endpoint())) push.subscribe(currentUser.get(), sub);
                });
            }
        });
        push.webPush().isNotificationDenied(ui, denied -> {
            if (denied) {
                status.setText("Ilmoitukset on estetty selaimen asetuksissa. Salli ne sivuston asetuksista.");
                subscribe.setEnabled(false);
            }
        });
    }

    /** Selain antoi tilauksen: tallennetaan se tälle käyttäjälle. */
    void onSubscribed(WebPushSubscription subscription) {
        AppUser me = currentUser.get();
        push.subscribe(me, subscription);
        show(true);
        Notification.show("Ilmoitukset otettu käyttöön tällä laitteella").addThemeVariants(NotificationVariant.SUCCESS);
    }

    /** Selain peruutti tilauksen: poistetaan se palvelimelta. */
    void onUnsubscribed(WebPushSubscription subscription) {
        push.unsubscribe(currentUser.get(), subscription.endpoint());
        show(false);
        Notification.show("Ilmoitukset poistettu käytöstä tällä laitteella");
    }

    private void show(boolean subscribed) {
        status.setText(subscribed ? "Ilmoitukset ovat käytössä tällä laitteella." : "Ilmoitukset ovat pois päältä tällä laitteella.");
        subscribe.setEnabled(true);
        subscribe.setVisible(!subscribed);
        unsubscribe.setVisible(subscribed);
    }

    boolean isSubscribedShown() {
        return unsubscribe.isVisible();
    }
}
