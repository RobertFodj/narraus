package com.narraus.ui;

import com.narraus.domain.AdminPolicy;
import com.narraus.domain.AppUser;
import com.narraus.domain.UserService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import jakarta.annotation.security.PermitAll;

@Route(value = "users", layout = MainLayout.class)
@RouteAlias(value = "people", layout = MainLayout.class) // vanha osoite toimii edelleen
@PageTitle("Käyttäjät | Narraus")
@PermitAll
public class UsersView extends VerticalLayout {

    private final UserService users;
    private final CurrentUser currentUser;
    private final AdminPolicy adminPolicy;
    private final VerticalLayout results = new VerticalLayout();

    public UsersView(UserService users, CurrentUser currentUser, AdminPolicy adminPolicy) {
        this.adminPolicy = adminPolicy;
        this.users = users;
        this.currentUser = currentUser;
        setMaxWidth("36em");
        getStyle().set("margin", "0 auto");

        TextField search = new TextField();
        search.setPlaceholder("Hae käyttäjiä nimellä…");
        search.setPrefixComponent(VaadinIcon.SEARCH.create());
        search.setWidthFull();
        search.setValueChangeMode(ValueChangeMode.LAZY);
        search.addValueChangeListener(e -> refresh(e.getValue()));

        results.setPadding(false);
        // Otsikko ja haku ovat "saari", joka pysyy paikallaan kun lista vierii sen alla
        VerticalLayout bar = new VerticalLayout(new H2("Käyttäjät"), search);
        bar.addClassName("island");
        bar.setPadding(false);
        bar.setSpacing(false);
        add(bar, results);
        refresh("");
    }

    private void refresh(String query) {
        results.removeAll();
        AppUser me = currentUser.get();
        for (AppUser u : users.search(query)) {
            if (u.equals(me)) continue;
            // Nimi on nappi: avaa käyttäjän oman sivun (samat tiedot ja julkaisut kuin profiilissa)
            Button name = new Button(u.getDisplayName(),
                    e -> UI.getCurrent().navigate(UserProfileView.class, u.getId()));
            name.addThemeVariants(ButtonVariant.TERTIARY);
            name.getStyle().set("flex-grow", "1").set("justify-content", "flex-start").set("font-weight", "600");
            Span followers = new Span(users.followerCount(u) + " seuraajaa");
            followers.getStyle().set("opacity", "0.7");

            HorizontalLayout row = new HorizontalLayout(name, followers);
            if (adminPolicy.isAdmin(me)) {
                // Vain ylläpitäjä näkee kirjautumistunnukset
                Span username = new Span("@" + u.getUsername());
                username.addClassName("admin-info");
                username.getStyle().set("opacity", "0.7");
                row.addComponentAtIndex(1, username);
            }
            row.setWidthFull();
            row.setAlignItems(FlexComponent.Alignment.CENTER);

            // Seuraa-nappi oikeassa laidassa vain, jos et vielä seuraa. Seuraamisen jälkeen nappia ei ole.
            if (!users.isFollowing(me, u)) {
                Button follow = new Button("Seuraa");
                follow.addThemeVariants(ButtonVariant.SMALL);
                follow.addClickListener(e -> {
                    users.toggleFollow(me, u);
                    followers.setText(users.followerCount(u) + " seuraajaa");
                    row.remove(follow);
                });
                row.add(follow);
            }
            results.add(row);
        }
        if (results.getComponentCount() == 0) results.add(new Span("Käyttäjiä ei löytynyt."));
    }
}
