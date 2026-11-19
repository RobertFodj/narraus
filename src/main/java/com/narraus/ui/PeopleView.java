package com.narraus.ui;

import com.narraus.domain.AppUser;
import com.narraus.domain.ChatService;
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
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

@Route(value = "people", layout = MainLayout.class)
@PageTitle("Ihmiset | Narraus")
@PermitAll
public class PeopleView extends VerticalLayout {

    private final UserService users;
    private final ChatService chats;
    private final CurrentUser currentUser;
    private final VerticalLayout results = new VerticalLayout();

    public PeopleView(UserService users, ChatService chats, CurrentUser currentUser) {
        this.users = users;
        this.chats = chats;
        this.currentUser = currentUser;
        setMaxWidth("36em");
        getStyle().set("margin", "0 auto");

        TextField search = new TextField();
        search.setPlaceholder("Hae kalastajia nimellä…");
        search.setPrefixComponent(VaadinIcon.SEARCH.create());
        search.setWidthFull();
        search.setValueChangeMode(ValueChangeMode.LAZY);
        search.addValueChangeListener(e -> refresh(e.getValue()));

        results.setPadding(false);
        add(new H2("Ihmiset"), search, results);
        refresh("");
    }

    private void refresh(String query) {
        results.removeAll();
        AppUser me = currentUser.get();
        for (AppUser u : users.search(query)) {
            if (u.equals(me)) continue;
            Span name = new Span(u.getDisplayName() + " (@" + u.getUsername() + ")");
            name.getStyle().set("flex-grow", "1");
            Span followers = new Span(users.followerCount(u) + " seuraajaa");
            followers.getStyle().set("opacity", "0.7");

            Button follow = new Button();
            follow.addThemeVariants(ButtonVariant.SMALL);
            Runnable label = () -> follow.setText(users.isFollowing(me, u) ? "Lopeta seuraaminen" : "Seuraa");
            follow.addClickListener(e -> {
                users.toggleFollow(me, u);
                label.run();
            });
            label.run();

            Button message = new Button("Viesti", VaadinIcon.CHAT.create(), e -> {
                var c = chats.openDirect(me, u);
                UI.getCurrent().navigate(ChatView.class, QueryParameters.of("c", String.valueOf(c.getId())));
            });
            message.addThemeVariants(ButtonVariant.SMALL);

            HorizontalLayout row = new HorizontalLayout(name, followers, follow, message);
            row.setWidthFull();
            row.setAlignItems(FlexComponent.Alignment.CENTER);
            results.add(row);
        }
        if (results.getComponentCount() == 0) results.add(new Span("Ketään ei löytynyt."));
    }
}
