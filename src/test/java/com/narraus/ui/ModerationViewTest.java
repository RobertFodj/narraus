package com.narraus.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.narraus.NarrausTest;
import com.narraus.domain.AppUser;
import com.narraus.domain.CatchPost;
import com.narraus.domain.ModerationService;
import com.narraus.domain.NotificationService;
import com.narraus.domain.PostService;
import com.narraus.domain.UserService;
import com.vaadin.browserless.BrowserlessUIContext;
import com.vaadin.browserless.SecuredBrowserlessApplicationContext;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;

/** Valitus ja palaute ylläpitäjän päätöksestä: kello, päätösikkuna, ylläpitäjän lista ja poiston syy. */
@NarrausTest
class ModerationViewTest {

    @Autowired
    private SecuredBrowserlessApplicationContext<Authentication> app;
    @Autowired
    private UserService users;
    @Autowired
    private PostService posts;
    @Autowired
    private ModerationService moderation;
    @Autowired
    private NotificationService notifications;

    private AppUser admin() {
        return users.find("robbe").orElseGet(() -> users.register("robbe", "Robbe Ylläpitäjä", "salasana123"));
    }

    private static Button bell(BrowserlessUIContext window) {
        return window.find(Button.class).all().stream().filter(b -> b.hasClassName("notification-bell")).findFirst().orElseThrow();
    }

    private static Dialog openDialog(BrowserlessUIContext window) {
        return window.find(Dialog.class).all().stream().filter(Dialog::isOpened).findFirst().orElseThrow();
    }

    @Test
    void adminDeletingAPostFromTheFeed_asksForAReason_andTheAuthorSeesItInTheBell() {
        AppUser owner = TestData.newUser(users, "Omistaja");
        CatchPost p = posts.create(owner, "Hauki", null, null, null, null, "Kuvaus", null, null);
        admin();

        try (var adminUser = app.newUser("robbe", "USER")) {
            var window = adminUser.newWindow();
            window.navigate("user/" + owner.getId(), UserProfileView.class);
            window.findButton().withText("Poista").click();

            ConfirmDialog confirm = window.find(ConfirmDialog.class).single();
            TextField reason = window.find(TextField.class).all().stream()
                    .filter(t -> t.hasClassName("moderation-reason-field")).findFirst().orElseThrow();
            window.test(reason).setValue("Kuva ei ole omasi");
            window.test(confirm).confirm();
        }
        assertTrue(posts.byAuthor(owner).stream().noneMatch(x -> x.getId().equals(p.getId())));

        try (var user = app.newUser(owner.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(FeedView.class);
            window.test(bell(window)).click();

            String text = openDialog(window).getElement().getTextRecursively();
            assertTrue(text.contains("Ylläpitäjä poisti julkaisusi: Hauki"), text);
        }
    }

    @Test
    void ownersOwnDelete_hasNoReasonField() {
        AppUser owner = TestData.newUser(users, "Omistaja");
        posts.create(owner, "Hauki", null, null, null, null, null, null, null);

        try (var user = app.newUser(owner.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(ProfileView.class);
            TestData.openSection(window, "Omat julkaisut");
            window.findButton().withText("Poista").click();

            assertTrue(window.find(TextField.class).all().stream()
                    .noneMatch(t -> t.hasClassName("moderation-reason-field")), "Oman julkaisun poistossa ei kysytä syytä");
        }
    }

    @Test
    void authorCanOpenTheDecisionFromTheBell_seeTheReason_andSendAComplaint() {
        AppUser owner = TestData.newUser(users, "Omistaja");
        posts.delete(posts.create(owner, "Hauki", null, null, null, null, "Kuvaus", null, null).getId(), admin(), "Epäselvä kuva");

        try (var user = app.newUser(owner.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(FeedView.class);
            window.test(bell(window)).click();
            window.test(window.find(Div.class).all().stream().filter(d -> d.hasClassName("notification-row")).findFirst()
                    .orElseThrow()).click();

            Dialog decision = window.find(ModerationDialog.class).single();
            assertTrue(decision.isOpened());
            String text = decision.getElement().getTextRecursively();
            assertTrue(text.contains("Syy: Epäselvä kuva"), text);
            assertTrue(text.contains("Julkaisun kuvaus: Kuvaus"), "Poistetun julkaisun kuvaus näkyy");

            window.find(TextArea.class).all().stream().filter(t -> "Valitus tai palaute".equals(t.getLabel()))
                    .findFirst().orElseThrow().setValue("Kuva oli oma, tässä lisätietoa.");
            window.findButton().withText("Lähetä").click();

            assertEquals("Kiitos, viesti lähetetty ylläpitäjälle", window.findNotification().getText());
            Long id = notifications.recent(owner).stream().filter(e -> e.targetId() != null && e.kind() != null)
                    .findFirst().orElseThrow().targetId();
            var action = moderation.actionFor(id, owner).orElseThrow();
            assertEquals("Kuva oli oma, tässä lisätietoa.", moderation.appealsOf(action).get(0).getText());
            assertTrue(decision.getElement().getTextRecursively().contains("Kuva oli oma, tässä lisätietoa."),
                    "Lähetetty viesti näkyy ikkunassa");
        }
    }

    @Test
    void emptyComplaint_isRejected() {
        AppUser owner = TestData.newUser(users, "Omistaja");
        posts.delete(posts.create(owner, "Hauki", null, null, null, null, null, null, null).getId(), admin(), null);

        try (var user = app.newUser(owner.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(FeedView.class);
            window.test(bell(window)).click();
            window.test(window.find(Div.class).all().stream().filter(d -> d.hasClassName("notification-row")).findFirst()
                    .orElseThrow()).click();

            window.findButton().withText("Lähetä").click();

            assertEquals("Viesti ei voi olla tyhjä", window.findNotification().getText());
        }
    }

    @Test
    void adminSeesTheComplaintsOnTheAdminPage() {
        AppUser owner = TestData.newUser(users, "Valittaja");
        AppUser admin = admin();
        posts.delete(posts.create(owner, "Hauki", null, null, null, null, null, null, null).getId(), admin, "Syy X");
        Long id = notifications.recent(owner).get(0).targetId();
        moderation.appeal(id, owner, "Tämä oli väärä päätös");

        try (var adminUser = app.newUser("robbe", "USER")) {
            var window = adminUser.newWindow();
            window.navigate(AdminView.class);

            assertTrue(window.find(com.vaadin.flow.component.html.H3.class).all().stream()
                    .anyMatch(h -> "Valitukset ja palaute".equals(h.getText())));
            @SuppressWarnings("rawtypes")
            var grids = window.find(com.vaadin.flow.component.grid.Grid.class).all();
            assertEquals(2, grids.size(), "Käyttäjälista ja valituslista");
        }
    }

    @Test
    void ordinaryUser_doesNotSeeTheComplaintsSection_butTheBellKeepsWorking() {
        AppUser normal = TestData.newUser(users, "Tavallinen");

        try (var user = app.newUser(normal.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(FeedView.class);

            assertFalse(window.find(com.vaadin.flow.component.html.H3.class).all().stream()
                    .anyMatch(h -> "Valitukset ja palaute".equals(h.getText())));
        }
    }
}
