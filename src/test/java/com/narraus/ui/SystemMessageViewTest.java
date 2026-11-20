package com.narraus.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.narraus.NarrausTest;
import com.narraus.domain.AppUser;
import com.narraus.domain.ChatService;
import com.narraus.domain.SystemMessage;
import com.narraus.domain.SystemMessageService;
import com.narraus.domain.UserService;
import com.vaadin.browserless.BrowserlessUIContext;
import com.vaadin.browserless.SecuredBrowserlessApplicationContext;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.messages.MessageInput;
import com.vaadin.flow.component.messages.MessageList;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.component.textfield.TextArea;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;

/** Narraus-viestit käyttöliittymässä: vastaanottajan osio ja ylläpitäjän oma lähetysnappi. */
@NarrausTest
class SystemMessageViewTest {

    @Autowired
    private SecuredBrowserlessApplicationContext<Authentication> app;
    @Autowired
    private UserService users;
    @Autowired
    private SystemMessageService system;
    @Autowired
    private ChatService chats;

    private AppUser admin() {
        return users.find("robbe").orElseGet(() -> users.register("robbe", "Robbe Ylläpitäjä", "salasana123"));
    }

    private static boolean hasButton(BrowserlessUIContext window, String text) {
        return window.find(Button.class).all().stream().anyMatch(b -> b.getText().equals(text));
    }

    private static boolean hasButtonStartingWith(BrowserlessUIContext window, String text) {
        return window.find(Button.class).all().stream().anyMatch(b -> b.getText().startsWith(text));
    }

    private static boolean hasSection(BrowserlessUIContext window, String text) {
        return window.find(Span.class).all().stream().anyMatch(s -> s.hasClassName("chat-section") && s.getText().startsWith(text));
    }

    private static boolean hasTitle(BrowserlessUIContext window, String text) {
        return window.find(H3.class).all().stream().anyMatch(h -> text.equals(h.getText()));
    }

    private static Optional<String> chatBadge(BrowserlessUIContext window) {
        SideNavItem item = window.find(SideNavItem.class).all().stream()
                .filter(i -> "Keskustelut".equals(i.getLabel())).findFirst().orElseThrow();
        Span badge = (Span) item.getSuffixComponent();
        return badge.isVisible() ? Optional.of(badge.getText()) : Optional.empty();
    }

    private static Checkbox toAllBox(BrowserlessUIContext window) {
        return window.find(Checkbox.class).all().stream().filter(c -> "Kaikille käyttäjille".equals(c.getLabel()))
                .findFirst().orElseThrow();
    }

    /** Kaikkien näkyvien ilmoitusten tekstit (useampi voi olla näkyvissä yhtä aikaa). */
    private static java.util.List<String> notificationTexts(BrowserlessUIContext window) {
        return window.find(com.vaadin.flow.component.notification.Notification.class).all().stream()
                .map(n -> window.test(n).getText()).toList();
    }

    private static TextArea messageText(BrowserlessUIContext window) {
        return window.find(TextArea.class).withLabel("Viesti").single();
    }

    // ---- ylläpitäjän lähetysnappi ----

    @Test
    void sendButton_isForTheAdminOnly_andSeparateFromNewGroup() {
        admin();
        AppUser normal = TestData.newUser(users, "Tavallinen");

        try (var adminUser = app.newUser("robbe", "USER"); var normalUser = app.newUser(normal.getUsername(), "USER")) {
            var adminWindow = adminUser.newWindow();
            var normalWindow = normalUser.newWindow();
            adminWindow.navigate(ChatView.class);
            normalWindow.navigate(ChatView.class);

            assertTrue(hasButton(adminWindow, "Lähetä Narraus-viesti"));
            assertTrue(hasButton(adminWindow, "Uusi ryhmä"), "Tavallinen viestitoiminto säilyy omana nappinaan");
            assertFalse(hasButton(normalWindow, "Lähetä Narraus-viesti"), "Tavallinen käyttäjä ei voi lähettää");
            assertTrue(hasButton(normalWindow, "Uusi ryhmä"));
        }
    }

    @Test
    void sendDialog_toAll_asksForConfirmation_andDeliversToEveryone() {
        admin();
        AppUser a = TestData.newUser(users, "A");
        AppUser b = TestData.newUser(users, "B");
        String text = "Joukkoviesti-" + UUID.randomUUID().toString().substring(0, 6);

        try (var adminUser = app.newUser("robbe", "USER")) {
            var window = adminUser.newWindow();
            window.navigate(ChatView.class);

            window.findButton().withText("Lähetä Narraus-viesti").click();
            assertTrue(toAllBox(window).getValue(), "Oletuksena kaikille");
            assertFalse(window.find(MultiSelectComboBox.class).single().isEnabled(), "Vastaanottajavalinta pois käytöstä");
            window.test(messageText(window)).setValue(text);
            window.findButton().withText("Lähetä").click();

            ConfirmDialog confirm = window.find(ConfirmDialog.class).single();
            assertTrue(confirm.isOpened(), "Kaikille lähetys vaatii vahvistuksen");
            assertTrue(system.messagesFor(a).stream().noneMatch(m -> m.getText().equals(text)), "Ennen vahvistusta ei ole lähetetty");

            window.test(confirm).confirm();

            assertTrue(system.messagesFor(a).stream().anyMatch(m -> m.getText().equals(text)));
            assertTrue(system.messagesFor(b).stream().anyMatch(m -> m.getText().equals(text)));
            assertTrue(window.findNotification().getText().startsWith("Viesti lähetetty "));
        }
    }

    @Test
    void sendDialog_toChosenUsers_sendsWithoutConfirmation_andOnlyToThem() {
        admin();
        AppUser a = TestData.newUser(users, "A");
        AppUser other = TestData.newUser(users, "Muu");
        String text = "Valituille-" + UUID.randomUUID().toString().substring(0, 6);

        try (var adminUser = app.newUser("robbe", "USER")) {
            var window = adminUser.newWindow();
            window.navigate(ChatView.class);
            window.findButton().withText("Lähetä Narraus-viesti").click();

            window.test(toAllBox(window)).uncheck();
            @SuppressWarnings("unchecked")
            MultiSelectComboBox<AppUser> recipients = window.find(MultiSelectComboBox.class).single();
            assertTrue(recipients.isEnabled(), "Vastaanottajat valittavissa, kun Kaikille on pois");
            recipients.setValue(Set.of(a));
            window.test(messageText(window)).setValue(text);
            window.findButton().withText("Lähetä").click();

            assertEquals("Viesti lähetetty 1 käyttäjälle", window.findNotification().getText());
            assertTrue(system.messagesFor(a).stream().anyMatch(m -> m.getText().equals(text)));
            assertTrue(system.messagesFor(other).stream().noneMatch(m -> m.getText().equals(text)));
        }
    }

    @Test
    void sendDialog_rejectsEmptyTextAndMissingRecipients() {
        admin();

        try (var adminUser = app.newUser("robbe", "USER")) {
            var window = adminUser.newWindow();
            window.navigate(ChatView.class);
            window.findButton().withText("Lähetä Narraus-viesti").click();

            window.test(toAllBox(window)).uncheck();
            window.test(messageText(window)).setValue("Teksti ilman vastaanottajia");
            window.findButton().withText("Lähetä").click();
            assertTrue(notificationTexts(window).contains("Valitse vähintään yksi vastaanottaja"));

            window.test(toAllBox(window)).check();
            window.test(messageText(window)).setValue("   ");
            window.findButton().withText("Lähetä").click();
            window.test(window.find(ConfirmDialog.class).single()).confirm();
            assertTrue(notificationTexts(window).contains("Viesti ei voi olla tyhjä"));
        }
    }

    // ---- vastaanottajan näkymä ----

    @Test
    void recipient_hasNoNarrausSection_untilAMessageArrives() {
        AppUser me = TestData.newUser(users, "Minä");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(ChatView.class);

            assertFalse(hasSection(window, "Narrauksen viestit"), "Ei tyhjää osiota, kun viestejä ei ole");
            assertFalse(hasButtonStartingWith(window, "Narraus"));
        }
    }

    @Test
    void narrausMessages_appearUnderTheirOwnHeading_apartFromConversations() {
        AppUser me = TestData.newUser(users, "Minä");
        AppUser friend = TestData.newUser(users, "Kaveri");
        chats.openDirect(me, friend);
        system.send(admin(), Set.of(me), "Huomio kaikille");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(ChatView.class);

            assertTrue(hasSection(window, "Narrauksen viestit"), "Oma otsikko Narrauksen viesteille");
            assertTrue(hasSection(window, "Yksityisviestit"), "Tavalliset keskustelut omalla otsikollaan");
            assertTrue(hasButton(window, "Narraus (1)"), "Merkintä näyttää lukemattomien määrän");
            assertTrue(hasButton(window, friend.getDisplayName()), "Tavalliset keskustelut säilyvät");
        }
    }

    @Test
    void openingNarraus_showsMessagesFromNarrausWithTheLogo_andNoReplying() {
        AppUser me = TestData.newUser(users, "Minä");
        system.send(admin(), Set.of(me), "Ensimmäinen");
        system.send(admin(), Set.of(me), "Toinen");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            ChatView view = window.navigate(ChatView.class);

            window.findButton().withText("Narraus (2)").click();

            assertTrue(hasTitle(window, "Narraus"));
            var shown = window.test(window.find(MessageList.class).single()).getMessages();
            assertEquals(2, shown.size());
            assertEquals("Ensimmäinen", shown.get(0).getText());
            assertEquals("Toinen", shown.get(1).getText());
            assertTrue(shown.stream().allMatch(m -> "Narraus".equals(m.getUserName())), "Lähettäjä on Narraus");
            assertTrue(shown.stream().allMatch(m -> ChatView.NARRAUS_LOGO.equals(m.getUserImage())),
                    "Käyttäjäkuvana Narrauksen logo");
            assertFalse(window.find(MessageInput.class).single().isEnabled(), "Narraus-viestiin ei voi vastata");
            assertTrue(view.hasClassName("chat-open"), "Puhelimella viestit täyttävät ruudun");
        }
    }

    @Test
    void theAdminIdentity_isNeverShownToTheRecipient() {
        AppUser me = TestData.newUser(users, "Minä");
        system.send(admin(), Set.of(me), "Salainen lähettäjä");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            ChatView view = window.navigate(ChatView.class);
            window.findButton().withText("Narraus (1)").click();

            String html = view.getElement().getOuterHTML();
            assertFalse(html.contains("Robbe"), "Ylläpitäjän nimi ei saa näkyä");
            assertFalse(html.contains("robbe"), "Ylläpitäjän tunnus ei saa näkyä");
            var shown = window.test(window.find(MessageList.class).single()).getMessages();
            assertTrue(shown.stream().noneMatch(m -> m.getUserName().contains("Robbe")));
        }
    }

    @Test
    void readingNarraus_clearsTheUnreadCount_andTheNavigationBadge() {
        AppUser me = TestData.newUser(users, "Minä");
        system.send(admin(), Set.of(me), "Yksi");
        system.send(admin(), Set.of(me), "Kaksi");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(ProfileView.class);
            assertEquals(Optional.of("2"), chatBadge(window), "Narraus-viestit näkyvät Keskustelut-merkissä");

            window.navigate(ChatView.class);
            window.findButton().withText("Narraus (2)").click();

            assertEquals(Optional.empty(), chatBadge(window), "Lukeminen poistaa merkin");
            assertEquals(0, system.unreadCount(me));
            assertTrue(hasButton(window, "Narraus"), "Merkintä säilyy ilman lukumäärää");
        }
    }

    @Test
    void aNewMessage_appearsLive_inTheOpenNarrausView() {
        AppUser me = TestData.newUser(users, "Minä");
        system.send(admin(), Set.of(me), "Vanha viesti");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(ChatView.class);
            window.findButton().withText("Narraus (1)").click();
            assertEquals(1, window.test(window.find(MessageList.class).single()).size());

            system.send(admin(), Set.of(me), "Uusi viesti");

            var shown = window.test(window.find(MessageList.class).single()).getMessages();
            assertEquals(2, shown.size(), "Uusi viesti ilmestyy heti");
            assertEquals("Uusi viesti", shown.get(1).getText());
            assertEquals(0, system.unreadCount(me), "Auki oleva viesti on heti luettu");
        }
    }

    @Test
    void aNewMessage_updatesTheEntryAndTheBadge_whenNarrausIsNotOpen() {
        AppUser me = TestData.newUser(users, "Minä");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(ChatView.class);
            assertFalse(hasSection(window, "Narrauksen viestit"));

            system.send(admin(), Set.of(me), "Yllätys");

            assertTrue(hasSection(window, "Narrauksen viestit"), "Osio ilmestyy, kun ensimmäinen viesti tulee");
            assertTrue(hasButton(window, "Narraus (1)"));
            assertEquals(Optional.of("1"), chatBadge(window));
        }
    }

    @Test
    void backButton_leavesNarraus_andReturnsToTheList() {
        AppUser me = TestData.newUser(users, "Minä");
        system.send(admin(), Set.of(me), "Viesti");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            ChatView view = window.navigate(ChatView.class);
            window.findButton().withText("Narraus (1)").click();
            assertTrue(view.hasClassName("chat-open"));

            window.find(Button.class).all().stream().filter(b -> b.hasClassName("chat-back")).findFirst()
                    .ifPresent(b -> window.test(b).click());

            assertFalse(view.hasClassName("chat-open"), "Takaisin palauttaa listanäkymän");
            assertTrue(hasTitle(window, "Valitse keskustelu"));
        }
    }

    @Test
    void openingARegularConversation_afterNarraus_switchesBack() {
        AppUser me = TestData.newUser(users, "Minä");
        AppUser friend = TestData.newUser(users, "Kaveri");
        chats.openDirect(me, friend);
        system.send(admin(), Set.of(me), "Viesti");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(ChatView.class);
            window.findButton().withText("Narraus (1)").click();

            window.findButton().withText(friend.getDisplayName()).click();

            assertTrue(hasTitle(window, friend.getDisplayName()));
            assertTrue(window.find(MessageInput.class).single().isEnabled(), "Tavallisessa keskustelussa voi taas kirjoittaa");
        }
    }

    @Test
    void messagesAreKeptSeparate_normalChatNeverShowsNarrausMessages() {
        AppUser me = TestData.newUser(users, "Minä");
        AppUser friend = TestData.newUser(users, "Kaveri");
        var c = chats.openDirect(me, friend);
        chats.send(c, friend, "Oikea keskusteluviesti");
        system.send(admin(), Set.of(me), "Narraus-viesti");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate("chat?c=" + c.getId(), ChatView.class);

            var shown = window.test(window.find(MessageList.class).single()).getMessages();
            assertEquals(1, shown.size());
            assertEquals("Oikea keskusteluviesti", shown.get(0).getText());
            assertEquals(friend.getDisplayName(), shown.get(0).getUserName());
        }
    }

    @Test
    void systemMessageEntity_hasOnlyTheDataTheViewNeeds() {
        AppUser me = TestData.newUser(users, "Minä");
        system.send(admin(), Set.of(me), "Teksti");

        SystemMessage m = system.messagesFor(me).get(0);

        assertEquals("Teksti", m.getText());
        assertFalse(m.isRead());
        assertTrue(m.getCreatedAt() != null);
    }

    @Test
    void narrausView_isReadOnly_andTheReplyFieldIsHiddenOnlyThere() throws Exception {
        AppUser me = TestData.newUser(users, "Minä");
        AppUser friend = TestData.newUser(users, "Kaveri");
        chats.openDirect(me, friend);
        system.send(admin(), Set.of(me), "Viesti");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            ChatView view = window.navigate(ChatView.class);
            assertFalse(view.hasClassName("chat-readonly"));

            window.findButton().withText("Narraus (1)").click();
            assertTrue(view.hasClassName("chat-readonly"), "Narraus-näkymässä kirjoituskenttä piilotetaan");

            window.findButton().withText(friend.getDisplayName()).click();
            assertFalse(view.hasClassName("chat-readonly"), "Tavallisessa keskustelussa kenttä on taas näkyvissä");
        }
        String css;
        try (var in = SystemMessageViewTest.class.getResourceAsStream("/META-INF/resources/styles.css")) {
            css = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        int rule = css.indexOf(".chat-view.chat-readonly vaadin-message-input");
        assertTrue(rule >= 0, "Tyyli kentän piilottamiseen puuttuu");
        String body = css.substring(rule, css.indexOf('}', rule));
        assertTrue(body.contains(".chat-compose-bar") && body.contains("display: none"));
    }
}
