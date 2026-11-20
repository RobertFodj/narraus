package com.narraus.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.narraus.NarrausTest;
import com.narraus.domain.AppUser;
import com.narraus.domain.ChatService;
import com.narraus.domain.Conversation;
import com.narraus.domain.UserService;
import com.vaadin.browserless.BrowserlessUIContext;
import com.vaadin.browserless.SecuredBrowserlessApplicationContext;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.messages.MessageInput;
import com.vaadin.flow.component.messages.MessageList;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;

/**
 * Keskustelunäkymän asettelu puhelimelle: lista ja keskustelu vuorotellen kapealla näytöllä, viestilista vierii
 * rajatussa tilassa ja syöttökenttä pysyy näkyvissä näppäimistön kanssa. Selaimen näppäimistöä ei voi testata
 * ilman selainta, joten tässä varmistetaan rakenne, tilat ja tyylisäännöt.
 */
@NarrausTest
class ChatLayoutTest {

    @Autowired
    private SecuredBrowserlessApplicationContext<Authentication> app;
    @Autowired
    private UserService users;
    @Autowired
    private ChatService chats;

    private static boolean hasTitle(BrowserlessUIContext window, String text) {
        return window.find(H3.class).all().stream().anyMatch(h -> text.equals(h.getText()));
    }

    private static boolean hasLayout(BrowserlessUIContext window, String className) {
        return window.find(VerticalLayout.class).all().stream().anyMatch(l -> l.hasClassName(className));
    }

    private static String css() throws Exception {
        try (var in = ChatLayoutTest.class.getResourceAsStream("/META-INF/resources/styles.css")) {
            assertNotNull(in, "styles.css puuttuu");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // ---- rakenne ja tilat ----

    @Test
    void chatView_hasTheLayoutClassesTheStylesRelyOn() {
        AppUser me = TestData.newUser(users, "Minä");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            ChatView view = window.navigate(ChatView.class);

            assertTrue(view.hasClassName("chat-view"));
            assertTrue(hasLayout(window, "chat-list"), "Keskustelulista tarvitsee luokan chat-list");
            assertTrue(hasLayout(window, "chat-main"), "Viestialue tarvitsee luokan chat-main");
            assertTrue(window.find(Button.class).all().stream().anyMatch(b -> b.hasClassName("chat-back")),
                    "Takaisin-nappi tarvitsee luokan chat-back");
        }
    }

    @Test
    void messageList_hasNoFixedFullHeight_soItScrollsInsteadOfGrowingThePage() {
        AppUser me = TestData.newUser(users, "Minä");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(ChatView.class);

            MessageList list = window.find(MessageList.class).single();
            assertNotEquals("100%", list.getHeight(), "100 % korkeus kasvattaisi sivua näppäimistön auetessa");
            assertEquals("0", list.getMinHeight().replaceAll("[a-z%]+$", ""), "Flex-lapsen min-height pitää olla 0");
        }
    }

    @Test
    void noConversationOpen_listIsShown_andOpenStateIsOff() {
        AppUser me = TestData.newUser(users, "Minä");
        AppUser friend = TestData.newUser(users, "Kaveri");
        chats.openDirect(me, friend);

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            ChatView view = window.navigate(ChatView.class);

            assertFalse(view.hasClassName("chat-open"), "Kapealla näytöllä näkyy lista, kun keskustelua ei ole auki");
        }
    }

    @Test
    void openingAConversation_switchesToTheOpenState() {
        AppUser me = TestData.newUser(users, "Minä");
        AppUser friend = TestData.newUser(users, "Kaveri");
        chats.openDirect(me, friend);

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            ChatView view = window.navigate(ChatView.class);

            window.findButton().withText(friend.getDisplayName()).click();

            assertTrue(view.hasClassName("chat-open"), "Avoin keskustelu: kapealla näytöllä näytetään viestit");
            assertTrue(window.find(MessageInput.class).single().isEnabled());
        }
    }

    @Test
    void openingByLink_startsInTheOpenState() {
        AppUser me = TestData.newUser(users, "Minä");
        AppUser friend = TestData.newUser(users, "Kaveri");
        Conversation c = chats.openDirect(me, friend);

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            ChatView view = window.navigate("chat?c=" + c.getId(), ChatView.class);

            assertTrue(view.hasClassName("chat-open"));
        }
    }

    @Test
    void backButton_returnsToTheList_andTheConversationCanBeReopened() {
        AppUser me = TestData.newUser(users, "Minä");
        AppUser friend = TestData.newUser(users, "Kaveri");
        Conversation c = chats.openDirect(me, friend);
        chats.send(c, friend, "hei");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            ChatView view = window.navigate("chat?c=" + c.getId(), ChatView.class);
            assertTrue(view.hasClassName("chat-open"));

            window.find(Button.class).all().stream().filter(b -> b.hasClassName("chat-back")).findFirst()
                    .ifPresent(b -> window.test(b).click());

            assertFalse(view.hasClassName("chat-open"), "Takaisin palauttaa listanäkymän");
            assertTrue(hasTitle(window, "Valitse keskustelu"));
            assertFalse(window.find(MessageInput.class).single().isEnabled());
            assertTrue(window.find(Button.class).all().stream()
                    .anyMatch(b -> friend.getDisplayName().equals(b.getText())), "Keskustelu on yhä listassa");

            window.findButton().withText(friend.getDisplayName()).click();

            assertTrue(view.hasClassName("chat-open"));
            assertEquals(1, window.test(window.find(MessageList.class).single()).size(), "Viestit palaavat");
        }
    }

    @Test
    void sendingAMessage_keepsTheConversationOpen() {
        AppUser me = TestData.newUser(users, "Minä");
        AppUser friend = TestData.newUser(users, "Kaveri");
        Conversation c = chats.openDirect(me, friend);

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            ChatView view = window.navigate("chat?c=" + c.getId(), ChatView.class);

            window.test(window.find(MessageInput.class).single()).send("Kirjoitan puhelimella");

            assertTrue(view.hasClassName("chat-open"), "Viestin lähetys ei saa palauttaa listaan");
            assertEquals(1, window.test(window.find(MessageList.class).single()).size());
        }
    }

    @Test
    void removedFromGroup_viewSwitchesBackToTheList() {
        AppUser me = TestData.newUser(users, "Minä");
        AppUser removed = TestData.newUser(users, "Poistettava");
        Conversation g = chats.createGroup(me, TestData.unique("Ryhmä"), java.util.Set.of(removed));

        try (var mine = app.newUser(me.getUsername(), "USER"); var theirs = app.newUser(removed.getUsername(), "USER")) {
            var myWindow = mine.newWindow();
            var removedWindow = theirs.newWindow();
            myWindow.navigate("chat?c=" + g.getId(), ChatView.class);
            ChatView removedView = removedWindow.navigate("chat?c=" + g.getId(), ChatView.class);
            assertTrue(removedView.hasClassName("chat-open"));

            chats.updateGroup(g.getId(), me, "Ryhmä", java.util.Set.of());

            // Ikkunakysely ajaa taustalta tulleet päivitykset (selaimeton ympäristö), ennen kuin luokka luetaan
            assertTrue(hasTitle(removedWindow, "Valitse keskustelu"));
            assertFalse(removedView.hasClassName("chat-open"), "Poistettu ei jää tyhjään keskusteluruutuun");
        }
    }

    // ---- tyylisäännöt ----

    @Test
    void styles_boundTheChatHeightToTheVisibleArea_andLetTheMessageListScroll() throws Exception {
        String css = css();

        assertTrue(css.contains(".chat-view"));
        assertFalse(css.contains("--app-height"), "Korkeutta ei sidota näppäimistöön");
        assertTrue(css.contains("100dvh"), "Varakorkeus dvh-yksiköllä, jos skriptiä ei ole ajettu");
        assertTrue(css.contains("vaadin-message-list"));
        assertTrue(css.contains("overflow-y: auto"), "Viestilista vierii");
        assertTrue(css.contains("min-height: 0"), "Flex-lapsi voi kutistua, jotta vieritys toimii");
        assertTrue(css.contains("vaadin-message-input"));
    }

    @Test
    void styles_showListOrConversationOnNarrowScreens() throws Exception {
        String css = css();

        assertTrue(css.contains("@media (max-width: 700px)"), "Puhelinasettelu on omassa media query:ssä");
        assertTrue(css.contains(".chat-view:not(.chat-open) .chat-main"), "Ilman avointa keskustelua viestialue piiloon");
        assertTrue(css.contains(".chat-view.chat-open .chat-list"), "Avoimen keskustelun aikana lista piiloon");
        assertTrue(css.contains(".chat-view .chat-back"));
        // Takaisin-nappi on piilossa leveällä näytöllä ja näkyvissä vain puhelinleveydellä
        int back = css.indexOf(".chat-view .chat-back");
        assertEquals("display: none;", css.substring(css.indexOf("{", back) + 1, css.indexOf("}", back)).strip());
    }

    @Test
    void phoneKeyboard_doesNotResizeTheMessageList() throws Exception {
        String css = css();
        assertTrue(css.contains("html:has(.chat-view)"), "Sivu ei vieri itse, kun keskustelu on auki");
        assertTrue(css.contains("font-size: 16px"), "16 px estää iOS:n zoomauksen kirjoittaessa");
        assertTrue(css.contains("100dvh"), "Korkeus on selaimen näkyvä korkeus, ei skriptillä seurattu");

        // Näppäimistön seurantaskripti poistettu: se laski viestilistan uudelleen joka animaatioruudulla
        org.junit.jupiter.api.Assertions.assertThrows(NoSuchFieldException.class,
                () -> ChatView.class.getDeclaredField("VIEWPORT_JS"));
        var focus = ChatView.class.getDeclaredField("FOCUS_PROXY_JS");
        focus.setAccessible(true);
        String script = (String) focus.get(null);
        assertTrue(script.contains("view.style.height"), "Korkeus jäädytetään kirjoituksen ajaksi");
    }

    // ---- puhelimen kirjoitusikkuna ----

    private static com.vaadin.flow.component.dialog.Dialog openComposeDialog(com.vaadin.browserless.BrowserlessUIContext w) {
        return w.find(com.vaadin.flow.component.dialog.Dialog.class).all().stream()
                .filter(d -> d.isOpened() && d.hasClassName("chat-compose")).findFirst().orElse(null);
    }

    private static Button composeBar(com.vaadin.browserless.BrowserlessUIContext w) {
        return w.find(Button.class).all().stream().filter(b -> b.hasClassName("chat-compose-bar")).findFirst().orElseThrow();
    }

    @Test
    void composeBar_isDisabledUntilAConversationIsOpen() {
        AppUser me = TestData.newUser(users, "Minä");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(ChatView.class);

            assertFalse(composeBar(window).isEnabled(), "Ilman avointa keskustelua ei voi kirjoittaa");
            assertEquals("Kirjoita viesti…", composeBar(window).getText());
        }
    }

    @Test
    void composeButton_opensATopAnchoredWriter_hidesTheBarWhileTyping_andSends() {
        AppUser me = TestData.newUser(users, "Minä");
        AppUser friend = TestData.newUser(users, "Kaveri");
        Conversation c = chats.openDirect(me, friend);

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            ChatView view = window.navigate("chat?c=" + c.getId(), ChatView.class);
            assertTrue(composeBar(window).isEnabled());
            assertFalse(view.hasClassName("composing"));
            assertNull(openComposeDialog(window), "Kirjoitusikkuna on piilossa ennen painallusta");

            window.test(composeBar(window)).click();

            var dialog = openComposeDialog(window);
            assertNotNull(dialog, "Napin painallus avaa kirjoitusikkunan");
            assertEquals("0", dialog.getTop(), "Ikkuna on näytön yläreunassa, joten teksti näkyy näppäimistön yläpuolella");
            assertEquals("calc(100vw - 1rem)", dialog.getWidth(), "Ikkuna mahtuu näytölle: leveys vähennettynä reunoilla");
            assertTrue(view.hasClassName("composing"), "Tavallinen viestipalkki piilotetaan kirjoittamisen ajaksi");

            window.test(window.find(com.vaadin.flow.component.textfield.TextArea.class)
                    .withPlaceholder("Kirjoita viesti…").single()).setValue("Moi puhelimesta");
            window.test(window.find(Button.class).all().stream().filter(b -> "Lähetä".equals(b.getText()))
                    .findFirst().orElseThrow()).click();

            assertEquals(1, chats.messages(c, me).size());
            assertEquals("Moi puhelimesta", chats.messages(c, me).get(0).getText());
            assertNull(openComposeDialog(window), "Ikkuna sulkeutuu lähetyksen jälkeen");
            assertFalse(view.hasClassName("composing"), "Viestipalkki palaa");
            assertTrue(view.hasClassName("chat-open"), "Keskustelu pysyy auki");
        }
    }

    @Test
    void composeSheet_blankMessage_isNotSent_andCancelKeepsEverything() {
        AppUser me = TestData.newUser(users, "Minä");
        AppUser friend = TestData.newUser(users, "Kaveri");
        Conversation c = chats.openDirect(me, friend);

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            ChatView view = window.navigate("chat?c=" + c.getId(), ChatView.class);

            window.test(composeBar(window)).click();
            window.test(window.find(Button.class).all().stream().filter(b -> "Lähetä".equals(b.getText()))
                    .findFirst().orElseThrow()).click();
            assertNotNull(openComposeDialog(window), "Tyhjällä viestillä ikkuna jää auki");
            assertEquals(0, chats.messages(c, me).size());

            window.test(window.find(Button.class).all().stream().filter(b -> "Peruuta".equals(b.getText()))
                    .findFirst().orElseThrow()).click();
            assertNull(openComposeDialog(window));
            assertFalse(view.hasClassName("composing"));
            assertEquals(0, chats.messages(c, me).size());
        }
    }

    @Test
    void styles_phoneUsesTheComposeButtonInsteadOfTheMessageBar() throws Exception {
        String css = css();

        int media = css.indexOf("@media (max-width: 700px)");
        String phone = css.substring(media);
        assertTrue(phone.contains(".chat-view .chat-main vaadin-message-input"), "Puhelimella viestipalkki piiloon");
        assertTrue(phone.contains(".chat-view.chat-open .chat-compose-bar"), "Kirjoitusnappi näkyviin puhelimella");
        assertTrue(phone.contains(".chat-view.composing .chat-compose-bar"), "Nappi piiloon kirjoittamisen ajaksi");
        String base = css.substring(0, media);
        int bar = base.indexOf(".chat-view .chat-compose-bar");
        assertEquals("display: none;", base.substring(base.indexOf("{", bar) + 1, base.indexOf("}", bar)).strip(),
                "Leveällä näytöllä kirjoitusnappi on piilossa");
    }

    @Test
    void longConversation_showsOnlyTheNewestMessagesFirst_andLoadsEarlierOnRequest() {
        AppUser me = TestData.newUser(users, "Minä");
        AppUser friend = TestData.newUser(users, "Kaveri");
        Conversation c = chats.openDirect(me, friend);
        for (int i = 1; i <= 45; i++) chats.send(c, me, "Viesti " + i);

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate("chat?c=" + c.getId(), ChatView.class);

            assertEquals(30, window.test(window.find(MessageList.class).single()).size(),
                    "Alussa vain uusimmat 30 viestiä, jotta sivu pysyy kevyenä");
            var earlier = window.find(Button.class).all().stream().filter(b -> b.hasClassName("chat-load-earlier"))
                    .findFirst().orElseThrow();
            assertTrue(earlier.isVisible());
            assertEquals("Näytä aiemmat viestit (15)", earlier.getText());

            window.test(earlier).click();

            assertEquals(45, window.test(window.find(MessageList.class).single()).size(), "Aiemmat haettiin");
            assertFalse(earlier.isVisible(), "Nappi katoaa, kun kaikki viestit näkyvät");
        }
    }

    @Test
    void shortConversation_hasNoLoadEarlierButton() {
        AppUser me = TestData.newUser(users, "Minä");
        AppUser friend = TestData.newUser(users, "Kaveri");
        Conversation c = chats.openDirect(me, friend);
        chats.send(c, me, "Moi");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate("chat?c=" + c.getId(), ChatView.class);

            assertTrue(window.find(Button.class).all().stream()
                    .noneMatch(b -> b.hasClassName("chat-load-earlier") && b.isVisible()));
        }
    }
}
