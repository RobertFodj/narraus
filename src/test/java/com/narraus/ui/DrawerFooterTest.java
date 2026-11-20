package com.narraus.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.narraus.NarrausTest;
import com.narraus.domain.AppUser;
import com.narraus.domain.UserService;
import com.vaadin.browserless.SecuredBrowserlessApplicationContext;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;

/** Sivupaneelin alatunniste: nimi omalla rivillään ja Kirjaudu ulos -nappi sen alla. */
@NarrausTest
class DrawerFooterTest {

    @Autowired
    private SecuredBrowserlessApplicationContext<Authentication> app;
    @Autowired
    private UserService users;

    @Test
    void logoutButton_isBelowTheName_notBesideIt() {
        AppUser me = TestData.newUser(users, "Minä");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(FeedView.class);

            Button logout = window.find(Button.class).all().stream()
                    .filter(b -> "Kirjaudu ulos".equals(b.getText())).findFirst().orElseThrow();
            var parent = logout.getParent().orElseThrow();

            assertInstanceOf(VerticalLayout.class, parent, "Nimi ja nappi ovat päällekkäin, eivät vierekkäin");
            assertFalse(parent instanceof HorizontalLayout);
            List<com.vaadin.flow.component.Component> children = parent.getChildren().toList();
            assertEquals(2, children.size());
            Span name = assertInstanceOf(Span.class, children.get(0), "Nimi on ensimmäisenä, ylhäällä");
            assertEquals(me.getDisplayName(), name.getText());
            assertEquals(logout, children.get(1), "Kirjaudu ulos on nimen alla");
        }
    }

    @Test
    void logoutButton_isStillThere_forEveryLoggedInUser() {
        AppUser me = TestData.newUser(users, "Minä");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(ProfileView.class);

            assertTrue(window.find(Button.class).all().stream().anyMatch(b -> "Kirjaudu ulos".equals(b.getText())));
        }
    }
}
