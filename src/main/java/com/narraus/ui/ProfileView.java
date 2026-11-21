package com.narraus.ui;

import com.narraus.domain.AccountService;
import com.narraus.domain.AppUser;
import com.narraus.domain.CatchPost;
import com.narraus.domain.GeocodingService;
import com.narraus.domain.PostService;
import com.narraus.domain.PushService;
import com.narraus.domain.UserService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import org.vaadin.firitin.appframework.MenuItem;
import java.util.List;

/**
 * Oma sivu: omat tiedot (tunnus näkyy vain täällä), laskurit, ilmoitusasetukset, omat julkaisut
 * muokkaus- ja poistonappeineen sekä tilin poisto. Salasanaa tai sen tiivistettä ei näytetä koskaan.
 */
@Route(value = "profile", layout = MainLayout.class)
@PageTitle("Oma tili | Narraus")
@PermitAll
@MenuItem(title = "Oma tili", icon = VaadinIcon.USER, order = 50)
public class ProfileView extends VerticalLayout {

    private final UserService users;
    private final PostService posts;
    private final AccountService accounts;
    private final PushService push;
    private final MapConfig mapConfig;
    private final GeocodingService geocoder;
    private final CurrentUser currentUser;
    private final com.narraus.domain.AdminPolicy adminPolicy;

    public ProfileView(UserService users, PostService posts, AccountService accounts, PushService push,
                       MapConfig mapConfig, GeocodingService geocoder, CurrentUser currentUser,
                       com.narraus.domain.AdminPolicy adminPolicy) {
        this.adminPolicy = adminPolicy;
        this.users = users;
        this.posts = posts;
        this.accounts = accounts;
        this.push = push;
        this.mapConfig = mapConfig;
        this.geocoder = geocoder;
        this.currentUser = currentUser;
        setMaxWidth("42em");
        setAlignItems(FlexComponent.Alignment.STRETCH);
        getStyle().set("margin", "0 auto");
        refresh();
    }

    /** Osiot, jotka aukeavat napeista; sisältö rakennetaan vasta kun osio avataan. */
    enum Section { NOTIFICATIONS, POSTS, FOLLOWERS, ACCOUNT, LEGAL }

    private Section open;

    private void refresh() {
        removeAll();
        AppUser me = currentUser.get();

        add(new H2("Oma tili"),
                new Span("Käyttäjätunnus: " + me.getUsername()),
                new Span("Näyttönimi: " + me.getDisplayName()),
                new Span("Seuraajia: " + users.followerCount(me)),
                new Span("Seurattavia: " + users.followingCount(me)),
                new Span("Julkaisuja: " + posts.postCount(me)));

        // Kirjaudu ulos löytyy täältä (puhelimella ei ole sivupaneelia, jonka alareunassa se on)
        Button logout = new Button("Kirjaudu ulos", VaadinIcon.SIGN_OUT.create(), e -> currentUser.logout());
        logout.addThemeVariants(ButtonVariant.TERTIARY);
        logout.addClassName("profile-logout");
        add(logout);

        // Napit: yksi osio kerrallaan auki, uusi painallus sulkee sen
        Div buttons = new Div();
        buttons.addClassName("profile-sections");
        if (adminPolicy.isAdmin(me)) {
            Button admin = new Button("Ylläpito", VaadinIcon.COG.create(),
                    e -> UI.getCurrent().navigate(AdminView.class));
            admin.addClassNames("profile-admin", "profile-section-button");
            buttons.add(admin);
        }
        LanguageMenu language = LanguageMenu.labeled();
        language.addClassName("profile-section-button");
        buttons.add(sectionButton("Ilmoitukset", VaadinIcon.BELL, Section.NOTIFICATIONS),
                sectionButton("Omat julkaisut", VaadinIcon.PICTURE, Section.POSTS),
                sectionButton("Seuraajat", VaadinIcon.USERS, Section.FOLLOWERS),
                sectionButton("Tilin muokkaus", VaadinIcon.USER_CARD, Section.ACCOUNT), language,
                sectionButton("Käyttöehdot ja tietosuoja", VaadinIcon.FILE_TEXT, Section.LEGAL));
        add(buttons);

        if (open != null) {
            VerticalLayout panel = switch (open) {
                case NOTIFICATIONS -> notificationsPanel();
                case POSTS -> postsPanel(me);
                case FOLLOWERS -> followersPanel(me);
                case ACCOUNT -> accountPanel();
                case LEGAL -> legalPanel();
            };
            panel.addClassName("profile-panel");
            add(panel);
        }
    }

    private Button sectionButton(String label, VaadinIcon icon, Section section) {
        Button b = new Button(label, icon.create(), e -> {
            open = open == section ? null : section;
            refresh();
        });
        b.addClassName("profile-section-button");
        b.getElement().setAttribute("aria-expanded", String.valueOf(open == section));
        if (open == section) b.addClassName("profile-section-active");
        return b;
    }

    private VerticalLayout notificationsPanel() {
        VerticalLayout panel = new VerticalLayout(new NotificationSettings(push, currentUser));
        panel.setPadding(false);
        return panel;
    }

    private VerticalLayout postsPanel(AppUser me) {
        VerticalLayout panel = new VerticalLayout(new H3("Omat julkaisut"));
        panel.setPadding(false);
        List<CatchPost> mine = posts.byAuthor(me);
        if (mine.isEmpty()) {
            panel.add(new Paragraph("Et ole vielä julkaissut mitään. Lisää ensimmäinen saaliisi Saaliit-sivulta."));
        }
        mine.forEach(p -> panel.add(postWithActions(p, me)));
        return panel;
    }

    /** Ketkä seuraavat sinua ja ketä sinä seuraat; myöhemmin tähän tulevat myös aiemmin voitetut kilpailut. */
    private VerticalLayout followersPanel(AppUser me) {
        VerticalLayout panel = new VerticalLayout();
        panel.setPadding(false);
        panel.add(new H3("Seuraajat"), userList(users.followersOf(me), "Kukaan ei seuraa sinua vielä."),
                new H3("Seuraat"), userList(users.followingOf(me), "Et seuraa vielä ketään. Löydät kalastajia Käyttäjät-sivulta."));
        return panel;
    }

    private VerticalLayout userList(List<AppUser> list, String emptyText) {
        VerticalLayout box = new VerticalLayout();
        box.setPadding(false);
        box.setSpacing(false);
        box.addClassName("profile-user-list");
        if (list.isEmpty()) box.add(new Paragraph(emptyText));
        for (AppUser u : list) {
            Button b = new Button(u.getDisplayName(), VaadinIcon.USER.create(),
                    e -> UI.getCurrent().navigate(UserProfileView.class, u.getId()));
            b.addThemeVariants(ButtonVariant.TERTIARY);
            b.addClassName("profile-user");
            box.add(b);
        }
        return box;
    }

    private static String legalHtml;

    /** Käyttöehdot ja tietosuojaseloste (suomeksi; teksti on tiedostossa legal/kayttoehdot-ja-tietosuoja.html). */
    private VerticalLayout legalPanel() {
        if (legalHtml == null) {
            try (var in = ProfileView.class.getResourceAsStream("/legal/kayttoehdot-ja-tietosuoja.html")) {
                legalHtml = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            } catch (java.io.IOException | NullPointerException e) {
                throw new IllegalStateException("Käyttöehtotiedosto puuttuu", e);
            }
        }
        VerticalLayout panel = new VerticalLayout(new H3("Käyttöehdot ja tietosuoja"),
                new com.vaadin.flow.component.Html(legalHtml));
        panel.setPadding(false);
        panel.addClassName("legal-panel");
        return panel;
    }

    /** Tilin muokkaus: selitys sekä Muokkaa tiliä- ja Poista tili -napit. */
    private VerticalLayout accountPanel() {
        Button editAccount = new Button("Muokkaa tiliä", VaadinIcon.COG.create(), e -> openEditAccountDialog());
        VerticalLayout edit = new VerticalLayout(new H3("Tilin tiedot"),
                new Paragraph("Voit vaihtaa näyttönimesi, käyttäjätunnuksesi ja salasanasi. "
                        + "Muut käyttäjät näkevät vain näyttönimesi."),
                editAccount);
        edit.setPadding(false);
        VerticalLayout panel = new VerticalLayout(edit, deleteAccountSection());
        panel.setPadding(false);
        return panel;
    }

    private VerticalLayout postWithActions(CatchPost p, AppUser me) {
        VerticalLayout box = new VerticalLayout(new CatchCard(posts, p, me),
                PostActions.row(posts, currentUser, mapConfig, geocoder, p, this::refresh));
        box.setPadding(false);
        return box;
    }

    private VerticalLayout deleteAccountSection() {
        Button open = new Button("Poista tili…", e -> openDeleteAccountDialog());
        open.addThemeVariants(ButtonVariant.ERROR);
        VerticalLayout section = new VerticalLayout(new H3("Poista tili"),
                new Paragraph("Tilin poisto on pysyvä: julkaisusi, viestisi, seuraamisesi ja ilmoituslaitteesi "
                        + "poistetaan, eikä sitä voi perua."),
                open);
        section.setPadding(false);
        return section;
    }

    private void openDeleteAccountDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Poista tili");
        PasswordField password = new PasswordField("Vahvista salasanalla");
        password.setWidthFull();
        dialog.add(password);

        Button confirm = new Button("Poista tili pysyvästi", e -> {
            try {
                accounts.deleteAccount(currentUser.get().getId(), password.getValue());
            } catch (IllegalArgumentException ex) {
                Notification.show(ex.getMessage()).addThemeVariants(NotificationVariant.ERROR);
                return;
            }
            dialog.close();
            currentUser.logout();
        });
        confirm.addThemeVariants(ButtonVariant.PRIMARY, ButtonVariant.ERROR);
        dialog.getFooter().add(new Button("Peruuta", e -> dialog.close()), confirm);
        dialog.open();
    }

    private void openEditAccountDialog() {
        AppUser me = currentUser.get();
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Muokkaa tiliä");
        dialog.setWidth("min(30em, 95vw)");

        TextField displayName = new TextField("Näyttönimi");
        displayName.setValue(me.getDisplayName());
        displayName.setRequired(true);
        TextField username = new TextField("Käyttäjätunnus");
        username.setValue(me.getUsername());
        PasswordField current = new PasswordField("Nykyinen salasana");
        PasswordField newPassword = new PasswordField("Uusi salasana (väh. 8 merkkiä)");
        PasswordField repeat = new PasswordField("Uusi salasana uudelleen");
        for (var f : List.of(displayName, username, current, newPassword, repeat)) f.setWidthFull();

        VerticalLayout form = new VerticalLayout(displayName, username,
                new Paragraph("Käyttäjätunnuksen vaihdon jälkeen kirjaudutaan uudelleen uudella tunnuksella."),
                new H3("Vaihda salasana"),
                new Paragraph("Jätä uudet salasanakentät tyhjiksi, jos et halua vaihtaa salasanaa. "
                        + "Tunnuksen tai salasanan vaihto vaatii nykyisen salasanan."),
                current, newPassword, repeat);
        form.setPadding(false);
        dialog.add(form);

        Button save = new Button("Tallenna", e -> {
            boolean relogin;
            try {
                if (!newPassword.getValue().equals(repeat.getValue())) {
                    throw new IllegalArgumentException("Uudet salasanat eivät täsmää");
                }
                relogin = users.updateAccount(me.getId(), displayName.getValue(), username.getValue(),
                        current.getValue(), newPassword.getValue());
            } catch (IllegalArgumentException ex) {
                Notification.show(ex.getMessage()).addThemeVariants(NotificationVariant.ERROR);
                return;
            }
            dialog.close();
            if (relogin) {
                // Istunto perustuu käyttäjätunnukseen, joten uusi tunnus vaatii uuden kirjautumisen
                Notification.show("Käyttäjätunnus vaihdettu. Kirjaudu uudelleen uudella tunnuksella.")
                        .addThemeVariants(NotificationVariant.SUCCESS);
                currentUser.logout();
            } else {
                Notification.show("Tiedot tallennettu").addThemeVariants(NotificationVariant.SUCCESS);
                refresh();
            }
        });
        save.addThemeVariants(ButtonVariant.PRIMARY);
        dialog.getFooter().add(new Button("Peruuta", e -> dialog.close()), save);
        dialog.open();
    }
}
