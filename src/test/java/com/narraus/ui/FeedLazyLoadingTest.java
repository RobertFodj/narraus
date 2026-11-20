package com.narraus.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.narraus.NarrausTest;
import com.narraus.domain.AppUser;
import com.narraus.domain.PostService;
import com.narraus.domain.UserService;
import com.vaadin.browserless.BrowserlessUIContext;
import com.vaadin.browserless.SecuredBrowserlessApplicationContext;
import com.vaadin.flow.component.button.Button;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;

/** Syöte lataa vain ensimmäisen erän; loput ladataan vasta kun käyttäjä vierittää listan loppuun. */
@NarrausTest
class FeedLazyLoadingTest {

    @Autowired
    private SecuredBrowserlessApplicationContext<Authentication> app;
    @Autowired
    private UserService users;
    @Autowired
    private PostService posts;

    private static long cards(BrowserlessUIContext window) {
        return window.find(com.vaadin.flow.component.orderedlayout.VerticalLayout.class).all().stream()
                .filter(v -> v.hasClassName("catch-card")).count();
    }

    private static Button more(BrowserlessUIContext window) {
        return window.find(Button.class).all().stream().filter(b -> b.hasClassName("feed-more")).findFirst().orElse(null);
    }

    @Test
    void onlyTheFirstBatchIsLoaded_andMoreComesWhenScrolledToTheEnd() {
        AppUser me = TestData.newUser(users, "Minä");
        for (int i = 0; i < FeedView.PAGE_SIZE * 2 + 3; i++) {
            posts.create(me, TestData.unique("Kala" + i), null, null, null, null, null, null, null);
        }

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(FeedView.class);

            assertEquals(FeedView.PAGE_SIZE, cards(window), "Vain ensimmäinen erä ladataan");
            assertTrue(more(window) != null, "Lisää ladataan, kun lista vieritetään loppuun");

            window.test(more(window)).click(); // selain tekee tämän itse, kun nappi tulee näkyviin

            assertEquals(FeedView.PAGE_SIZE * 2, cards(window));
            assertTrue(more(window) != null);

            window.test(more(window)).click();

            assertTrue(cards(window) >= FeedView.PAGE_SIZE * 2 + 3, "Kaikki omat julkaisut lopulta ladattu");
        }
    }

    @Test
    void shortFeed_hasNoLoadMore() {
        AppUser me = TestData.newUser(users, "Minä");
        AppUser other = TestData.newUser(users, "Toinen");
        users.toggleFollow(me, other);

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(FeedView.class);
            window.find(com.vaadin.flow.component.tabs.Tabs.class).single().setSelectedIndex(1); // Seuraamani

            assertTrue(cards(window) < FeedView.PAGE_SIZE);
            assertTrue(more(window) == null, "Ei lisää-nappia, kun kaikki on jo ladattu");
        }
    }
}
