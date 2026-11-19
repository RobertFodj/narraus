package com.narraus.ui;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import jakarta.annotation.security.PermitAll;

@PermitAll
public class MainLayout extends AppLayout {

    public MainLayout(CurrentUser currentUser) {
        H1 title = new H1("🎣 Narraus");
        title.getStyle().set("font-size", "1.25rem").set("margin", "0");
        addToNavbar(new DrawerToggle(), title);

        SideNav nav = new SideNav();
        nav.addItem(new SideNavItem("Saaliit", FeedView.class, VaadinIcon.PICTURE.create()));
        nav.addItem(new SideNavItem("Ihmiset", PeopleView.class, VaadinIcon.USERS.create()));
        nav.addItem(new SideNavItem("Keskustelut", ChatView.class, VaadinIcon.CHAT.create()));
        addToDrawer(nav);

        Button logout = new Button("Kirjaudu ulos", VaadinIcon.SIGN_OUT.create(), e -> currentUser.logout());
        logout.addThemeVariants(ButtonVariant.TERTIARY);
        HorizontalLayout footer = new HorizontalLayout(new Span(currentUser.get().getDisplayName()), logout);
        footer.setPadding(true);
        footer.setSpacing(true);
        footer.getStyle().set("flex-wrap", "wrap");
        addToDrawer(footer);
    }
}
