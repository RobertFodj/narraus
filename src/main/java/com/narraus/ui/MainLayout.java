package com.narraus.ui;

import com.narraus.domain.AdminPolicy;
import com.narraus.domain.AppUser;
import com.narraus.domain.ChatBroadcaster;
import com.narraus.domain.FeedBroadcaster;
import com.narraus.domain.SystemMessageBroadcaster;
import com.narraus.domain.UnreadService;
import com.narraus.domain.UserService;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.component.UIDetachedException;
import com.vaadin.flow.shared.Registration;
import jakarta.annotation.security.PermitAll;
import java.util.ArrayList;
import java.util.List;

@PermitAll
public class MainLayout extends AppLayout implements AfterNavigationObserver {

    private final UnreadService unread;
    private final UserService users;
    private final ChatBroadcaster chatBroadcaster;
    private final FeedBroadcaster feedBroadcaster;
    private final SystemMessageBroadcaster systemBroadcaster;
    private final AppUser me;

    /** Asennettu sovellus (PWA) avautuu omassa ikkunassaan: display-mode standalone (iOS: navigator.standalone). */
    private static final String CLIENT_JS =
            "return (window.matchMedia && window.matchMedia('(display-mode: standalone)').matches)"
                    + " || window.navigator.standalone === true;";

    private final Span feedBadge = badge();
    private final Span chatBadge = badge();
    private final List<Registration> registrations = new ArrayList<>();

    public MainLayout(CurrentUser currentUser, UnreadService unread, ChatBroadcaster chatBroadcaster,
                      FeedBroadcaster feedBroadcaster, UserService users, AdminPolicy adminPolicy,
                      SystemMessageBroadcaster systemBroadcaster) {
        this.systemBroadcaster = systemBroadcaster;
        this.users = users;
        this.unread = unread;
        this.chatBroadcaster = chatBroadcaster;
        this.feedBroadcaster = feedBroadcaster;
        this.me = currentUser.get();

        H1 title = new H1("🎣 Narraus");
        title.getStyle().set("font-size", "1.25rem").set("margin", "0");
        addToNavbar(new DrawerToggle(), title);

        SideNav nav = new SideNav();
        SideNavItem feed = new SideNavItem("Saaliit", FeedView.class, VaadinIcon.PICTURE.create());
        feed.setSuffixComponent(feedBadge);
        SideNavItem chat = new SideNavItem("Keskustelut", ChatView.class, VaadinIcon.CHAT.create());
        chat.setSuffixComponent(chatBadge);
        nav.addItem(feed);
        nav.addItem(new SideNavItem("Käyttäjät", UsersView.class, VaadinIcon.USERS.create()));
        nav.addItem(chat);
        nav.addItem(new SideNavItem("Kartta", MapView.class, VaadinIcon.MAP_MARKER.create()));
        nav.addItem(new SideNavItem("Kilpailut", EventsView.class, VaadinIcon.TROPHY.create()));
        nav.addItem(new SideNavItem("Oma sivu", ProfileView.class, VaadinIcon.USER.create()));
        if (adminPolicy.isAdmin(me)) {
            nav.addItem(new SideNavItem("Ylläpito", AdminView.class, VaadinIcon.COG.create()));
        }
        addToDrawer(nav);

        // Alatunniste kiinni paneelin alareunassa: nimi ja Kirjaudu ulos valikon rivien kaltaisena rivinä
        // (mitat ja värit ovat tyyleissä: .drawer-footer, .drawer-user, .drawer-logout)
        Button logout = new Button("Kirjaudu ulos", VaadinIcon.SIGN_OUT.create(), e -> currentUser.logout());
        logout.addThemeVariants(ButtonVariant.TERTIARY);
        logout.addClassName("drawer-logout");
        Span userName = new Span(me.getDisplayName());
        userName.addClassName("drawer-user");
        VerticalLayout footer = new VerticalLayout(userName, logout);
        footer.addClassName("drawer-footer");
        footer.setPadding(false);
        footer.setSpacing(false);
        addToDrawer(footer);

        refreshBadges();
    }

    private static Span badge() {
        Span s = new Span();
        s.addClassName("nav-badge");
        s.setVisible(false);
        return s;
    }

    /** Näyttää merkin vain, kun uutta on; 1-9 numerona ja siitä ylöspäin "9+". */
    static String badgeText(long count) {
        return count > 9 ? "9+" : String.valueOf(count);
    }

    private static void show(Span badge, long count, String description) {
        badge.setVisible(count > 0);
        if (count > 0) {
            badge.setText(badgeText(count));
            badge.getElement().setAttribute("role", "img");
            badge.getElement().setAttribute("aria-label", count + " " + description);
        }
    }

    void refreshBadges() {
        show(feedBadge, unread.newPosts(me), "uutta julkaisua");
        show(chatBadge, unread.unreadMessages(me), "lukematonta viestiä");
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        refreshBadges();
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        UI ui = attachEvent.getUI();
        registrations.add(chatBroadcaster.register(id -> access(ui)));
        registrations.add(feedBroadcaster.register(id -> access(ui)));
        registrations.add(systemBroadcaster.register(userId -> {
            if (me.getId().equals(userId)) access(ui); // Narraus-viesti tuli minulle
        }));
        registrations.add(ComponentUtil.addListener(ui, UnreadChanged.class, e -> refreshBadges()));
        refreshBadges();
        // Kirjataan, käytetäänkö sovellusta asennettuna sovelluksena (PWA) vai selaimessa (ylläpitäjä näkee tämän)
        ui.getPage().executeJs(CLIENT_JS).then(Boolean.class,
                installed -> users.recordClient(me.getId(), Boolean.TRUE.equals(installed)));
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        registrations.forEach(Registration::remove);
        registrations.clear();
        super.onDetach(detachEvent);
    }

    private void access(UI ui) {
        try {
            ui.access(this::refreshBadges);
        } catch (UIDetachedException ignored) {
            // selain on jo suljettu
        }
    }
}
