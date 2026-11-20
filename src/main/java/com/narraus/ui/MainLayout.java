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
import com.vaadin.flow.component.contextmenu.ContextMenu;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Nav;
import com.vaadin.flow.router.RouteConfiguration;
import com.vaadin.flow.router.RouterLink;
import java.util.LinkedHashMap;
import java.util.Map;
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
    private final Span feedBarBadge = badge();
    private final Span chatBarBadge = badge();
    /** Alapalkin linkit reitin osoitteen mukaan; aktiivinen korostetaan sivunvaihdossa. */
    private final Map<String, RouterLink> barLinks = new LinkedHashMap<>();
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
        // Oma tili (nimi + Kirjaudu ulos) ylhäällä oikealla; näkyy vain puhelimella pystyssä, jossa sivupaneelia ei ole
        Button account = new Button(VaadinIcon.USER_CHECK.create());
        account.addThemeVariants(ButtonVariant.TERTIARY);
        account.addClassName("account-button");
        account.getElement().setAttribute("aria-label", "Oma tili");
        ContextMenu accountMenu = new ContextMenu(account);
        accountMenu.setOpenOnClick(true);
        accountMenu.addItem(me.getDisplayName()).setEnabled(false);
        accountMenu.addItem("Kirjaudu ulos", e -> currentUser.logout());
        addToNavbar(new DrawerToggle(), title, account);

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

        // Puhelin pystyssä: kelluva alapalkki ("saari") samoilla valikoilla kuin sivupaneelissa
        Nav bar = new Nav();
        bar.addClassName("bottom-bar");
        bar.getElement().setAttribute("aria-label", "Päävalikko");
        addBarLink(bar, "Saaliit", FeedView.class, VaadinIcon.PICTURE, feedBarBadge);
        addBarLink(bar, "Käyttäjät", UsersView.class, VaadinIcon.USERS, null);
        addBarLink(bar, "Viestit", ChatView.class, VaadinIcon.CHAT, chatBarBadge);
        addBarLink(bar, "Kartta", MapView.class, VaadinIcon.MAP_MARKER, null);
        addBarLink(bar, "Kilpailut", EventsView.class, VaadinIcon.TROPHY, null);
        addBarLink(bar, "Oma sivu", ProfileView.class, VaadinIcon.USER, null);
        if (adminPolicy.isAdmin(me)) {
            addBarLink(bar, "Ylläpito", AdminView.class, VaadinIcon.COG, null);
        }
        getElement().appendChild(bar.getElement());

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

    private void addBarLink(Nav bar, String label, Class<? extends com.vaadin.flow.component.Component> view,
                            VaadinIcon icon, Span badge) {
        RouterLink link = new RouterLink(view);
        link.addClassName("bottom-tab");
        link.getElement().setAttribute("aria-label", label);
        var iconBox = new Span(icon.create());
        iconBox.addClassName("bottom-tab-icon");
        if (badge != null) iconBox.add(badge);
        Span text = new Span(label);
        text.addClassName("bottom-tab-label");
        link.add(iconBox, text);
        bar.add(link);
        barLinks.put(RouteConfiguration.forApplicationScope().getUrl(view), link);
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
        long posts = unread.newPosts(me);
        long messages = unread.unreadMessages(me);
        show(feedBadge, posts, "uutta julkaisua");
        show(feedBarBadge, posts, "uutta julkaisua");
        show(chatBadge, messages, "lukematonta viestiä");
        show(chatBarBadge, messages, "lukematonta viestiä");
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        String path = event.getLocation().getFirstSegment();
        barLinks.forEach((url, link) -> {
            boolean active = url.equals(path) || (!url.isEmpty() && path.startsWith(url + "/"));
            if (active) link.getElement().setAttribute("aria-current", "page");
            else link.getElement().removeAttribute("aria-current");
        });
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
