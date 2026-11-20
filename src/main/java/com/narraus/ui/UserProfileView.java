package com.narraus.ui;

import com.narraus.domain.AdminPolicy;
import com.narraus.domain.AdminService;
import com.narraus.domain.AppUser;
import com.narraus.domain.CatchPost;
import com.narraus.domain.ChatService;
import com.narraus.domain.PostService;
import com.narraus.domain.UserService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.NotFoundException;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import java.util.List;
import java.util.Optional;

/**
 * Toisen käyttäjän julkinen sivu: vain näyttönimi, laskurit ja julkaisut.
 * Ei kirjautumistunnusta eikä muita henkilökohtaisia tietoja. Osoite käyttää numeerista id:tä,
 * jottei kirjautumistunnus vuoda URL:iin.
 */
@Route(value = "user", layout = MainLayout.class)
@PageTitle("Käyttäjä | Narraus")
@PermitAll
public class UserProfileView extends VerticalLayout implements HasUrlParameter<Long> {

    private final UserService users;
    private final PostService posts;
    private final ChatService chats;
    private final CurrentUser currentUser;
    private final AdminPolicy adminPolicy;
    private final AdminService adminService;

    public UserProfileView(UserService users, PostService posts, ChatService chats, CurrentUser currentUser,
                           AdminPolicy adminPolicy, AdminService adminService) {
        this.adminPolicy = adminPolicy;
        this.adminService = adminService;
        this.users = users;
        this.posts = posts;
        this.chats = chats;
        this.currentUser = currentUser;
        setMaxWidth("42em");
        setAlignItems(FlexComponent.Alignment.STRETCH);
        getStyle().set("margin", "0 auto");
    }

    @Override
    public void setParameter(BeforeEvent event, Long userId) {
        AppUser me = currentUser.get();
        if (me.getId().equals(userId)) {
            event.forwardTo(ProfileView.class);
            return;
        }
        Optional<AppUser> found = users.findById(userId);
        if (found.isEmpty()) {
            event.rerouteToError(NotFoundException.class, "Käyttäjää ei löytynyt");
            return;
        }
        AppUser user = found.get();

        removeAll();
        Span counts = new Span();
        Button follow = new Button();
        Runnable update = () -> {
            counts.setText(users.followerCount(user) + " seuraajaa  ·  " + users.followingCount(user)
                    + " seurattavaa  ·  " + posts.postCount(user) + " julkaisua");
            boolean following = users.isFollowing(me, user);
            follow.setText(following ? "Lopeta seuraaminen" : "Seuraa");
            follow.removeThemeVariants(ButtonVariant.PRIMARY);
            if (!following) follow.addThemeVariants(ButtonVariant.PRIMARY);
        };
        follow.addClickListener(e -> {
            users.toggleFollow(me, user);
            update.run();
        });
        update.run();

        Button message = new Button("Viesti", VaadinIcon.CHAT.create(), e -> {
            var c = chats.openDirect(me, user);
            UI.getCurrent().navigate(ChatView.class, QueryParameters.of("c", String.valueOf(c.getId())));
        });

        HorizontalLayout header = new HorizontalLayout(new H2(user.getDisplayName()),
                new HorizontalLayout(follow, message));
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setWidthFull();
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        Button back = new Button("Käyttäjät", VaadinIcon.ARROW_LEFT.create(),
                e -> UI.getCurrent().navigate(UsersView.class));
        back.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.SMALL);
        back.addClassName("profile-back");
        add(back, header, counts);
        if (adminPolicy.isAdmin(me)) add(adminInfo(me, user));

        add(new H3("Julkaisut"));
        List<CatchPost> theirs = posts.byAuthor(user);
        if (theirs.isEmpty()) {
            add(new Paragraph("Ei vielä julkaisuja."));
        }
        theirs.forEach(p -> add(new CatchCard(posts, p, me)));
    }

    /** Ylläpitäjälle näkyvät lisätiedot: tunnus, kilpailut ja laitetyyppi. Muille tätä ei näytetä. */
    private VerticalLayout adminInfo(AppUser admin, AppUser user) {
        var overview = adminService.overviewOf(admin, user.getId());
        VerticalLayout box = new VerticalLayout();
        box.addClassName("admin-info");
        box.setPadding(false);
        box.setSpacing(false);
        box.add(new H3("Ylläpitäjän tiedot"));
        box.add(new Span("Tunnus: " + overview.username()));
        box.add(new Span("Kilpailut: " + (overview.events().isEmpty() ? "ei yhtään"
                : overview.events().stream().map(e -> e.getName()).collect(java.util.stream.Collectors.joining(", ")))));
        box.add(new Span("Laite: " + overview.clientLabel()));
        box.add(new Span("Ilmoituslaitteita: " + overview.pushDevices()));
        return box;
    }
}
