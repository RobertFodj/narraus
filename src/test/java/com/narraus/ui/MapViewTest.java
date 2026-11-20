package com.narraus.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.narraus.NarrausTest;
import com.narraus.domain.AppUser;
import com.narraus.domain.CatchPost;
import com.narraus.domain.PostService;
import com.narraus.domain.UserService;
import com.vaadin.browserless.SecuredBrowserlessApplicationContext;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.sidenav.SideNavItem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;

@NarrausTest
class MapViewTest {

    @Autowired
    private SecuredBrowserlessApplicationContext<Authentication> app;
    @Autowired
    private UserService users;
    @Autowired
    private PostService posts;

    @Test
    void sideNav_hasMapLink() {
        AppUser me = TestData.newUser(users, "Minä");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(FeedView.class);

            assertTrue(window.find(SideNavItem.class).all().stream().anyMatch(i -> "Kartta".equals(i.getLabel())));
        }
    }

    @Test
    void map_showsOnlyPostsWithCoordinates() {
        AppUser me = TestData.newUser(users, "Minä");
        AppUser other = TestData.newUser(users, "Toinen");
        CatchPost mine = posts.create(me, TestData.unique("Hauki"), null, null, null, null, null, null, null, 61.5, 25.5);
        CatchPost others = posts.create(other, TestData.unique("Kuha"), null, null, null, null, null, null, null, 65.0, 25.4);
        CatchPost noPlace = posts.create(other, TestData.unique("Ahven"), null, null, null, "Jossain", null, null, null);

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            MapView view = window.navigate(MapView.class);

            var ids = view.shownPosts().stream().map(CatchPost::getId).toList();
            assertTrue(ids.contains(mine.getId()), "Oma sijainnillinen julkaisu pitäisi näkyä");
            assertTrue(ids.contains(others.getId()), "Myös toisten julkaisut näkyvät kartalla");
            assertFalse(ids.contains(noPlace.getId()), "Julkaisua ilman sijaintia ei voi piirtää");
            assertEquals(view.shownPosts().size(), view.markerCount());
        }
    }

    @Test
    void map_startsOverFinland() {
        AppUser me = TestData.newUser(users, "Minä");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            MapView view = window.navigate(MapView.class);

            var center = view.map().getCenter();
            assertNotNull(center);
            // Suomi: pituuspiiri n. 20-32 E, leveyspiiri n. 59,5-70,2 N
            assertTrue(center.x > 20 && center.x < 32, "Keskipisteen pituuspiirin pitää olla Suomessa: " + center.x);
            assertTrue(center.y > 59.5 && center.y < 70.2, "Keskipisteen leveyspiirin pitää olla Suomessa: " + center.y);
            Double zoom = view.map().getZoomLevel();
            assertNotNull(zoom);
            assertTrue(zoom >= 3 && zoom <= 6, "Alkuzoomilla koko maan pitää mahtua näkyviin: " + zoom);
        }
    }

    @Test
    void clickingAPoint_opensThePostDetails() {
        AppUser me = TestData.newUser(users, "Minä");
        AppUser other = TestData.newUser(users, "Toinen");
        String species = TestData.unique("Taimen");
        CatchPost post = posts.create(other, species, 2.5, null, null, "Tenojoki", "Hieno päivä", null, null, 69.9, 27.0);

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            MapView view = window.navigate(MapView.class);

            view.openDetails(post);

            assertTrue(window.find(Dialog.class).all().stream().anyMatch(Dialog::isOpened), "Tietodialogin pitäisi olla auki");
            assertTrue(window.find(CatchCard.class).all().size() >= 1);
            assertTrue(window.find(H2.class).all().stream().anyMatch(h -> species.equals(h.getText())));
        }
    }

    @Test
    void anonymousUser_isRedirectedToLogin() {
        try (var anonymous = app.newUser()) {
            anonymous.newWindow().navigate("map", LoginView.class);
        }
    }
}
