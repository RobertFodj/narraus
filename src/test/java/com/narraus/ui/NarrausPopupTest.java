package com.narraus.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.narraus.NarrausTest;
import com.narraus.domain.AppUser;
import com.narraus.domain.SystemMessageService;
import com.narraus.domain.UnreadService;
import com.narraus.domain.UserService;
import com.vaadin.browserless.SecuredBrowserlessApplicationContext;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.notification.Notification;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;

/** Narraus-viesti ponnahtaa näytölle saarena, ja "Ymmärrän" kuittaa sen luetuksi. */
@NarrausTest
class NarrausPopupTest {

    @Autowired
    private SecuredBrowserlessApplicationContext<Authentication> app;
    @Autowired
    private UserService users;
    @Autowired
    private SystemMessageService system;
    @Autowired
    private UnreadService unread;

    private AppUser admin() {
        return users.find("robbe").orElseGet(() -> users.register("robbe", "Robbe Ylläpitäjä", "salasana123"));
    }

    private static Button understood(com.vaadin.browserless.BrowserlessUIContext window) {
        return window.find(Button.class).all().stream().filter(b -> "Ymmärrän".equals(b.getText())).findFirst().orElse(null);
    }

    @Test
    void unreadNarrausMessage_popsUpAsAnIsland_withUnderstoodButton() {
        AppUser me = TestData.newUser(users, "Minä");
        String text = "Huoltokatko-" + UUID.randomUUID().toString().substring(0, 6);
        system.send(admin(), Set.of(me), text);

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(FeedView.class);

            Notification popup = window.find(Notification.class).all().stream()
                    .filter(Notification::isOpened).findFirst().orElseThrow();
            assertEquals(Notification.Position.TOP_CENTER, popup.getPosition());
            assertEquals(0, popup.getDuration(), "Pysyy kunnes kuitataan");
            assertTrue(popup.getElement().getChildren().anyMatch(c -> c.getTextRecursively().contains(text)));
            assertTrue(popup.getElement().getChildren().anyMatch(c -> c.getTextRecursively().contains("Narraus")));
            assertTrue(understood(window) != null, "Ymmärrän-nappi");
            assertEquals(1, unread.unreadMessages(me), "Viesti on myös Viestit-osaston ilmoituksena");
        }
    }

    @Test
    void understood_closesThePopup_andMarksTheMessageRead() {
        AppUser me = TestData.newUser(users, "Minä");
        system.send(admin(), Set.of(me), "Luettavaksi");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(FeedView.class);

            window.test(understood(window)).click();

            assertEquals(0, system.unreadCount(me));
            assertEquals(0, unread.unreadMessages(me));
            assertTrue(window.find(Notification.class).all().stream().noneMatch(Notification::isOpened));
        }
    }

    @Test
    void noPopup_whenThereIsNothingUnread() {
        AppUser me = TestData.newUser(users, "Minä");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(FeedView.class);

            assertTrue(understood(window) == null);
        }
    }

    @Test
    void alreadyReadMessage_doesNotPopUpAgain() {
        AppUser me = TestData.newUser(users, "Minä");
        system.send(admin(), Set.of(me), "Vanha");
        system.markAllRead(me);

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(FeedView.class);

            assertFalse(understood(window) != null);
        }
    }

    @Test
    void messageArrivingWhileOnline_popsUpWithoutReload() {
        AppUser me = TestData.newUser(users, "Minä");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(FeedView.class);
            assertTrue(understood(window) == null);

            system.send(admin(), Set.of(me), "Tuli kesken kaiken");

            assertTrue(understood(window) != null, "Saapunut viesti ponnahtaa näytölle");
        }
    }

    @Test
    void styles_haveThePopupIsland() throws Exception {
        String css;
        try (var in = NarrausPopupTest.class.getResourceAsStream("/META-INF/resources/styles.css")) {
            css = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        assertTrue(css.contains(".narraus-popup {") && css.contains(".narraus-popup-ok {"));
    }
}
