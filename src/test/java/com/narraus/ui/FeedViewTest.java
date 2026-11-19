package com.narraus.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.narraus.NarrausTest;
import com.narraus.domain.AppUser;
import com.narraus.domain.PostService;
import com.narraus.domain.UserService;
import com.vaadin.browserless.SecuredBrowserlessApplicationContext;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;

@NarrausTest
class FeedViewTest {

    @Autowired
    private SecuredBrowserlessApplicationContext<Authentication> app;

    @Autowired
    private UserService users;

    @Autowired
    private PostService posts;

    @Test
    void newCatch_isShownInFeedWithStats() {
        String username = "kalastaja-" + UUID.randomUUID().toString().substring(0, 8);
        AppUser me = users.register(username, "Kalle", "salasana123");
        String species = "Hauki-" + UUID.randomUUID().toString().substring(0, 8);
        posts.create(me, species, 3.5, 80.0, null, "Päijänne", "Hieno kala", null, null);

        try (var user = app.newUser(username, "USER")) {
            var window = user.newWindow();
            window.navigate(FeedView.class);

            window.find(H2.class).withText(species).single();
            assertTrue(window.find(Span.class).all().stream()
                            .anyMatch(s -> s.getText().contains("Paino 3,5 kg") && s.getText().contains("Päijänne")),
                    "Saaliskortin pitäisi näyttää paino ja paikka");
        }
    }
}
