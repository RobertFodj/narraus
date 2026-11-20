package com.narraus.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.narraus.NarrausTest;
import com.narraus.domain.AppUser;
import com.narraus.domain.CatchPost;
import com.narraus.domain.CompetitionEvent;
import com.narraus.domain.EventMetric;
import com.narraus.domain.EventService;
import com.narraus.domain.PostService;
import com.narraus.domain.UserService;
import com.vaadin.browserless.BrowserlessUIContext;
import com.vaadin.browserless.SecuredBrowserlessApplicationContext;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;

/** Kilpailun saaliista alkuperäiseen julkaisuun ja julkaisun paikasta kartalle. */
@NarrausTest
class PostLinksTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3, 4};

    @Autowired
    private SecuredBrowserlessApplicationContext<Authentication> app;
    @Autowired
    private UserService users;
    @Autowired
    private PostService posts;
    @Autowired
    private EventService events;

    private AppUser admin() {
        return users.find("robbe").orElseGet(() -> users.register("robbe", "Robbe Ylläpitäjä", "salasana123"));
    }

    private static boolean hasCard(BrowserlessUIContext window, Long postId) {
        return window.find(VerticalLayout.class).all().stream()
                .anyMatch(c -> c.hasClassName("catch-card") && ("post-" + postId).equals(c.getId().orElse("")));
    }

    private static Span place(BrowserlessUIContext window) {
        return window.find(Span.class).all().stream().filter(s -> s.hasClassName("catch-place")).findFirst().orElse(null);
    }

    // ---- kilpailun saalis -> alkuperäinen julkaisu ----

    @Test
    void cardsHaveAnIdentifier_soTheFeedCanScrollToThem() {
        AppUser me = TestData.newUser(users, "Minä");
        CatchPost post = posts.create(me, "Hauki", null, null, null, null, null, null, null);

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(FeedView.class);

            assertTrue(hasCard(window, post.getId()));
        }
    }

    @Test
    void feedWithPostParameter_loadsListUntilThePostIsThere_evenFarDown() {
        AppUser me = TestData.newUser(users, "Minä");
        CatchPost old = posts.create(me, "Ahven", null, null, null, null, null, null, null, null, null,
                Instant.now().minus(Duration.ofDays(30)));
        for (int i = 0; i < FeedView.PAGE_SIZE * 2; i++) {
            posts.create(me, TestData.unique("Kala" + i), null, null, null, null, null, null, null);
        }

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(FeedView.class);
            assertFalse(hasCard(window, old.getId()), "Vanha julkaisu on listan loppupäässä, ei ensimmäisessä erässä");

            window.navigate(UsersView.class);
            window.navigate("?post=" + old.getId(), FeedView.class);

            assertTrue(hasCard(window, old.getId()), "Lista latautuu kunnes julkaisu löytyy");
        }
    }

    @Test
    void unknownPostParameter_isIgnored() {
        AppUser me = TestData.newUser(users, "Minä");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate("?post=9999999", FeedView.class);
            window.navigate(UsersView.class);
            window.navigate("?post=ei-numero", FeedView.class);

            assertTrue(window.find(FeedView.class).all().size() == 1, "Näkymä aukeaa normaalisti");
        }
    }

    @Test
    void competitionFish_linksToTheOriginalPostOnTheFeed() {
        AppUser fisher = TestData.newUser(users, "Kalastaja");
        CompetitionEvent ev = events.create(admin(), TestData.unique("Kisa"), null, Instant.now().minus(Duration.ofHours(1)),
                Instant.now().plus(Duration.ofHours(1)), EventMetric.WEIGHT, null, 1);
        events.join(ev.getId(), fisher);
        CatchPost post = posts.create(fisher, "Hauki", 4.0, null, null, null, null, PNG, "image/png", null, null,
                Instant.now().minus(Duration.ofMinutes(10)));

        try (var user = app.newUser(fisher.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate("event/" + ev.getId(), EventView.class);

            Component body = window.find(Div.class).all().stream()
                    .filter(d -> d.hasClassName("event-fish-text") && d.hasClassName("event-fish-link"))
                    .findFirst().orElseThrow();
            window.test(body).click();

            assertEquals(FeedView.class, window.getCurrentView().getClass(), "Saalis vie Saaliit-sivulle");
            assertTrue(hasCard(window, post.getId()), "Alkuperäinen julkaisu on listassa");
        }
    }

    // ---- julkaisun paikka -> kartta ----

    private static boolean cardHasPlaceLink(BrowserlessUIContext window, Long postId) {
        var card = window.find(VerticalLayout.class).all().stream()
                .filter(c -> ("post-" + postId).equals(c.getId().orElse(""))).findFirst().orElseThrow();
        return card.getElement().getChildren().flatMap(c -> c.getChildren())
                .anyMatch(c -> c.getClassList().contains("catch-place"));
    }

    @Test
    void postPlace_isALink_onlyWhenThePostHasCoordinates() {
        AppUser me = TestData.newUser(users, "Minä");
        CatchPost withPoint = posts.create(me, "Hauki", null, null, null, "Päijänne", null, null, null, 61.5, 25.5);
        CatchPost withoutPoint = posts.create(me, "Ahven", null, null, null, "Salainen paikka", null, null, null, null, null);

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(FeedView.class);

            assertTrue(cardHasPlaceLink(window, withPoint.getId()), "Pisteellisen julkaisun paikka on linkki");
            assertFalse(cardHasPlaceLink(window, withoutPoint.getId()), "Pisteettömän paikka on vain teksti");
            assertTrue(window.find(Span.class).all().stream()
                    .anyMatch(s -> "📍 Salainen paikka".equals(s.getText())), "Paikka näkyy silti tekstinä");
        }
    }

    @Test
    void clickingThePlace_opensTheMapTab_zoomedToThePost() {
        AppUser me = TestData.newUser(users, "Minä");
        posts.create(me, "Hauki", null, null, null, "Päijänne", null, null, null, 61.5, 25.5);

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(FeedView.class);
            assertTrue(window.find(MapView.class).all().isEmpty());

            Span placeLink = window.find(Span.class).all().stream()
                    .filter(s -> s.hasClassName("catch-place") && "📍 Päijänne".equals(s.getText())).findFirst().orElseThrow();
            window.test(placeLink).click();

            assertEquals(2, window.find(com.vaadin.flow.component.tabs.Tabs.class).single().getSelectedIndex(),
                    "Kartalla-välilehti avautuu");
            MapView map = window.find(MapView.class).single();
            assertEquals(25.5, map.map().getCenter().x, 1e-9);
            assertEquals(61.5, map.map().getCenter().y, 1e-9);
            assertEquals(MapView.FOCUS_ZOOM, map.map().getZoomLevel(), 1e-9);
        }
    }

    @Test
    void mapParameter_fromAnotherPage_opensTheMapOnThePost() {
        AppUser me = TestData.newUser(users, "Minä");
        CatchPost post = posts.create(me, "Kuha", null, null, null, "Lahti", null, null, null, 60.98, 25.66);

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate("?map=" + post.getId(), FeedView.class);

            MapView map = window.find(MapView.class).single();
            assertEquals(60.98, map.map().getCenter().y, 1e-9);
        }
    }
}
