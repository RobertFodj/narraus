package com.narraus.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.narraus.NarrausTest;
import com.narraus.domain.AppUser;
import com.narraus.domain.PushService;
import com.narraus.domain.UserService;
import com.vaadin.browserless.BrowserlessUIContext;
import com.vaadin.browserless.SecuredBrowserlessApplicationContext;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.server.webpush.WebPushKeys;
import com.vaadin.flow.server.webpush.WebPushSubscription;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;

/** Oman sivun Ilmoitukset-osio. Selaimen tilausvaihetta ei voi ajaa ilman selainta, joten sen paluukutsut kutsutaan suoraan. */
@NarrausTest
class NotificationSettingsTest {

    @Autowired
    private SecuredBrowserlessApplicationContext<Authentication> app;
    @Autowired
    private UserService users;
    @Autowired
    private PushService push;

    private static boolean hasSpan(BrowserlessUIContext window, String text) {
        return window.find(Span.class).all().stream().anyMatch(s -> text.equals(s.getText()));
    }

    private static boolean visibleButton(BrowserlessUIContext window, String text) {
        return window.find(Button.class).all().stream().anyMatch(b -> text.equals(b.getText()) && b.isVisible());
    }

    private static WebPushSubscription device() {
        return new WebPushSubscription("https://push.example.test/" + UUID.randomUUID(),
                new WebPushKeys("p256dh", "auth"));
    }

    @Test
    void ownPage_hasNotificationsSection_offByDefault() {
        AppUser me = TestData.newUser(users, "Minä");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(ProfileView.class);
            TestData.openSection(window, "Ilmoitukset");

            assertTrue(window.find(H3.class).all().stream().anyMatch(h -> "Ilmoitukset".equals(h.getText())));
            assertTrue(hasSpan(window, "Ilmoitukset ovat pois päältä tällä laitteella."));
            assertTrue(visibleButton(window, "Ota ilmoitukset käyttöön"));
            assertFalse(visibleButton(window, "Poista ilmoitukset käytöstä"));
        }
    }

    @Test
    void section_explainsWhatYouGetNotifiedAbout_andTheIphoneRequirement() {
        AppUser me = TestData.newUser(users, "Minä");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(ProfileView.class);
            TestData.openSection(window, "Ilmoitukset");

            String text = window.find(Paragraph.class).all().stream().map(Paragraph::getText)
                    .filter(t -> t.contains("Saat ilmoituksen")).findFirst().orElseThrow();
            assertTrue(text.contains("viesteistä"));
            assertTrue(text.contains("julkaisuista"));
            assertTrue(text.contains("iPhonessa"), "iOS vaatii asennuksen aloitusnäyttöön: siitä pitää kertoa");
        }
    }

    @Test
    void afterTheBrowserSubscribes_theDeviceIsStored_andTheUiSwitches() {
        AppUser me = TestData.newUser(users, "Minä");
        var subscription = device();

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            ProfileView view = window.navigate(ProfileView.class);
            TestData.openSection(window, "Ilmoitukset");
            NotificationSettings settings = window.find(NotificationSettings.class).single();

            settings.onSubscribed(subscription);

            assertEquals(1, push.deviceCount(me));
            assertTrue(push.isSubscribed(me, subscription.endpoint()));
            assertTrue(hasSpan(window, "Ilmoitukset ovat käytössä tällä laitteella."));
            assertTrue(visibleButton(window, "Poista ilmoitukset käytöstä"));
            assertFalse(visibleButton(window, "Ota ilmoitukset käyttöön"));
        }
    }

    @Test
    void afterTheBrowserUnsubscribes_theDeviceIsRemoved_andTheUiSwitchesBack() {
        AppUser me = TestData.newUser(users, "Minä");
        var subscription = device();
        push.subscribe(me, subscription);

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            ProfileView view = window.navigate(ProfileView.class);
            TestData.openSection(window, "Ilmoitukset");
            NotificationSettings settings = window.find(NotificationSettings.class).single();

            settings.onUnsubscribed(subscription);

            assertEquals(0, push.deviceCount(me));
            assertTrue(visibleButton(window, "Ota ilmoitukset käyttöön"));
            assertFalse(visibleButton(window, "Poista ilmoitukset käytöstä"));
        }
    }

    @Test
    void unsubscribing_onlyAffectsTheLoggedInUsersDevice() {
        AppUser me = TestData.newUser(users, "Minä");
        AppUser other = TestData.newUser(users, "Toinen");
        var othersDevice = device();
        push.subscribe(other, othersDevice);

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            ProfileView view = window.navigate(ProfileView.class);
            TestData.openSection(window, "Ilmoitukset");
            NotificationSettings settings = window.find(NotificationSettings.class).single();

            settings.onUnsubscribed(othersDevice); // yritys poistaa toisen laite

            assertTrue(push.isSubscribed(other, othersDevice.endpoint()), "Toisen käyttäjän laite säilyy");
        }
    }

    @Test
    void otherUsersPage_hasNoNotificationSettings() {
        AppUser me = TestData.newUser(users, "Katsoja");
        AppUser other = TestData.newUser(users, "Toinen");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate("user/" + other.getId(), UserProfileView.class);

            assertTrue(window.find(H3.class).all().stream().noneMatch(h -> "Ilmoitukset".equals(h.getText())));
        }
    }

    @Test
    void whenPushIsNotConfigured_theSectionSaysSoAndOffersNoButtons() {
        PushService disabled = mock(PushService.class);
        when(disabled.isEnabled()).thenReturn(false);

        NotificationSettings settings = new NotificationSettings(disabled, null);

        assertTrue(settings.getChildren().anyMatch(c -> c instanceof Paragraph p
                && p.getText().contains("eivät ole käytössä")));
        assertEquals(0, settings.getChildren().filter(Button.class::isInstance).count(), "Ei nappeja ilman avaimia");
    }
}
