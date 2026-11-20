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
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import java.util.List;

/**
 * Oma sivu: omat tiedot (tunnus näkyy vain täällä), laskurit, ilmoitusasetukset, omat julkaisut
 * muokkaus- ja poistonappeineen sekä tilin poisto. Salasanaa tai sen tiivistettä ei näytetä koskaan.
 */
@Route(value = "profile", layout = MainLayout.class)
@PageTitle("Oma sivu | Narraus")
@PermitAll
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

    private void refresh() {
        removeAll();
        AppUser me = currentUser.get();

        add(new H2("Oma sivu"),
                new Span("Käyttäjätunnus: " + me.getUsername()),
                new Span("Näyttönimi: " + me.getDisplayName()),
                new Span("Seuraajia: " + users.followerCount(me)),
                new Span("Seurattavia: " + users.followingCount(me)),
                new Span("Julkaisuja: " + posts.postCount(me)));

        if (adminPolicy.isAdmin(me)) {
            // Ylläpito löytyy ylläpitäjän omalta sivulta, ilmoitusasetusten yläpuolelta
            com.vaadin.flow.component.button.Button admin = new com.vaadin.flow.component.button.Button("Ylläpito",
                    com.vaadin.flow.component.icon.VaadinIcon.COG.create(),
                    e -> com.vaadin.flow.component.UI.getCurrent().navigate(AdminView.class));
            admin.addClassName("profile-admin");
            add(admin);
        }

        add(new NotificationSettings(push, currentUser));

        add(new H3("Omat julkaisut"));
        List<CatchPost> mine = posts.byAuthor(me);
        if (mine.isEmpty()) {
            add(new Paragraph("Et ole vielä julkaissut mitään. Lisää ensimmäinen saaliisi Saaliit-sivulta."));
        }
        mine.forEach(p -> add(postWithActions(p, me)));

        // Toiseksi alimpana: tilin ja omien tietojen muokkaus (ei julkaisuja)
        Button editAccount = new Button("Muokkaa tiliä", VaadinIcon.COG.create(), e -> openEditAccountDialog());
        add(editAccount);

        add(deleteAccountSection());
    }

    private VerticalLayout postWithActions(CatchPost p, AppUser me) {
        Button edit = new Button("Muokkaa", VaadinIcon.EDIT.create(),
                e -> new CatchDialog(posts, currentUser, mapConfig, geocoder, p, this::refresh).open());
        edit.addThemeVariants(ButtonVariant.SMALL);
        Button delete = new Button("Poista", VaadinIcon.TRASH.create(), e -> confirmDelete(p, me));
        delete.addThemeVariants(ButtonVariant.SMALL, ButtonVariant.ERROR);

        VerticalLayout box = new VerticalLayout(new CatchCard(posts, p, me), new HorizontalLayout(edit, delete));
        box.setPadding(false);
        return box;
    }

    private void confirmDelete(CatchPost p, AppUser me) {
        ConfirmDialog confirm = new ConfirmDialog();
        confirm.setHeader("Poistetaanko julkaisu?");
        confirm.setText("Julkaisu \"" + p.getSpecies() + "\" poistetaan pysyvästi tykkäyksineen ja kuvineen.");
        confirm.setCancelable(true);
        confirm.setCancelText("Peruuta");
        confirm.setConfirmText("Poista");
        confirm.setConfirmButtonTheme("error primary");
        confirm.addConfirmListener(e -> {
            posts.delete(p.getId(), me);
            refresh();
        });
        confirm.open();
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
