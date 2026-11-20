package com.narraus.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.narraus.NarrausTest;
import com.narraus.domain.AppUser;
import com.narraus.domain.ChatService;
import com.narraus.domain.Conversation;
import com.narraus.domain.UserService;
import com.vaadin.browserless.SecuredBrowserlessApplicationContext;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.messages.MessageInput;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.TextField;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;

/** "Saaret": haku, otsikot ja kirjoituskenttä pysyvät paikoillaan, kun sisältö vierii niiden alla. */
@NarrausTest
class IslandLayoutTest {

    @Autowired
    private SecuredBrowserlessApplicationContext<Authentication> app;
    @Autowired
    private UserService users;
    @Autowired
    private ChatService chats;

    private static Stream<Component> descendants(Component c) {
        return Stream.concat(Stream.of(c), c.getChildren().flatMap(IslandLayoutTest::descendants));
    }

    private static List<Component> islands(Component view) {
        return descendants(view).filter(c -> c.hasClassName("island")).toList();
    }

    @Test
    void css_islandsAreStickyFloatingCards() throws Exception {
        String css;
        try (InputStream in = getClass().getResourceAsStream("/META-INF/resources/styles.css")) {
            css = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        int rule = css.indexOf(".island {");
        assertTrue(rule >= 0, "Saarella on oma tyylisääntö");
        String body = css.substring(rule, css.indexOf("}", rule));
        assertTrue(body.contains("position: sticky"), "Saari pysyy paikallaan vierittäessä");
        assertTrue(body.contains("top:"), "Saari kiinnittyy ylälaitaan");
        assertTrue(body.contains("z-index: 1;"), "Saari on sisällön päällä mutta sivupaneelin (z-index 2) alla");
        assertTrue(css.contains("vaadin-app-layout[overlay][drawer-opened] .island"), "Saaret piiloutuvat paneelin ollessa auki");
        int hide = css.indexOf("vaadin-app-layout[overlay][drawer-opened] .island");
        assertTrue(css.substring(css.indexOf("{", hide), css.indexOf("}", hide)).contains("visibility: hidden"));
        assertTrue(body.contains("box-shadow") && body.contains("border-radius"), "Saari erottuu kelluvana palana");
    }

    @Test
    void feed_titleButtonAndTabsAreOneIsland() {
        AppUser me = TestData.newUser(users, "Minä");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            FeedView feed = window.navigate(FeedView.class);

            var islands = islands(feed);
            assertEquals(1, islands.size());
            var inside = descendants(islands.get(0)).toList();
            assertTrue(inside.stream().anyMatch(c -> c instanceof Tabs), "Välilehdet ovat saaressa");
            assertTrue(inside.stream().anyMatch(c -> c instanceof H2 h && "Saaliit".equals(h.getText())));
            assertTrue(inside.stream().anyMatch(c -> c instanceof Button b && "Uusi saalis".equals(b.getText())));
        }
    }

    @Test
    void users_titleAndSearchAreOneIsland() {
        AppUser me = TestData.newUser(users, "Minä");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            UsersView view = window.navigate(UsersView.class);

            var islands = islands(view);
            assertEquals(1, islands.size());
            var inside = descendants(islands.get(0)).toList();
            assertTrue(inside.stream().anyMatch(c -> c instanceof TextField), "Haku on saaressa");
            assertTrue(inside.stream().anyMatch(c -> c instanceof H2 h && "Käyttäjät".equals(h.getText())));
        }
    }

    @Test
    void chat_listHeader_conversationTitle_andWritingBarAreIslands() {
        AppUser me = TestData.newUser(users, "Minä");
        AppUser friend = TestData.newUser(users, "Kaveri");
        Conversation c = chats.openDirect(me, friend);

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            ChatView view = window.navigate("chat?c=" + c.getId(), ChatView.class);

            var islands = islands(view);
            assertTrue(islands.stream().anyMatch(i -> descendants(i)
                            .anyMatch(d -> d instanceof Button b && "Uusi ryhmä".equals(b.getText()))),
                    "Keskustelulistan otsikko ja Uusi ryhmä -nappi ovat saari");
            assertTrue(islands.stream().anyMatch(i -> descendants(i)
                            .anyMatch(d -> d instanceof com.vaadin.flow.component.html.H3 h
                                    && friend.getDisplayName().equals(h.getText()))),
                    "Avoimen keskustelun otsikko on saari");
            assertTrue(islands.stream().anyMatch(i -> i instanceof MessageInput), "Viestin kirjoituskenttä on saari");
            assertTrue(islands.stream().anyMatch(i -> i instanceof Button b && b.hasClassName("chat-compose-bar")),
                    "Puhelimen kirjoitusnappi on saari");
        }
    }
}
