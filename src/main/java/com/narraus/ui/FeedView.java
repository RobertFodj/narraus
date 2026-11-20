package com.narraus.ui;

import com.narraus.domain.AppUser;
import com.narraus.domain.CatchPost;
import com.narraus.domain.FeedBroadcaster;
import com.narraus.domain.GeocodingService;
import com.narraus.domain.PostService;
import com.narraus.domain.UnreadService;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.component.UIDetachedException;
import com.vaadin.flow.shared.Registration;
import jakarta.annotation.security.PermitAll;
import java.util.List;

@Route(value = "", layout = MainLayout.class)
@PageTitle("Saaliit | Narraus")
@PermitAll
public class FeedView extends VerticalLayout {

    private final PostService posts;
    private final CurrentUser currentUser;
    private final UnreadService unread;
    private final FeedBroadcaster broadcaster;
    private Registration registration;
    private final Tabs tabs = new Tabs(new Tab("Kaikki"), new Tab("Seuraamani"));
    private final Div list = new Div();

    public FeedView(PostService posts, CurrentUser currentUser, UnreadService unread, FeedBroadcaster broadcaster,
                    MapConfig mapConfig, GeocodingService geocoder) {
        this.posts = posts;
        this.currentUser = currentUser;
        this.unread = unread;
        this.broadcaster = broadcaster;
        setMaxWidth("44em");
        setAlignItems(FlexComponent.Alignment.STRETCH);
        getStyle().set("margin", "0 auto");

        Button add = new Button("Uusi saalis", VaadinIcon.PLUS.create(),
                e -> new CatchDialog(posts, currentUser, mapConfig, geocoder, this::refresh).open());
        add.addThemeVariants(ButtonVariant.PRIMARY);
        HorizontalLayout header = new HorizontalLayout(new H2("Saaliit"), add);
        header.setWidthFull();
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        header.setAlignItems(FlexComponent.Alignment.CENTER);

        // Kaksi saalista vierekkäin myös puhelimella (ks. .feed-list styles.css:ssä)
        list.addClassName("feed-list");
        tabs.addSelectedChangeListener(e -> refresh());
        // Otsikko, nappi ja välilehdet ovat "saari", joka pysyy paikallaan kun saaliit vierivät sen alla
        VerticalLayout bar = new VerticalLayout(header, tabs);
        bar.addClassName("island");
        bar.setPadding(false);
        bar.setSpacing(false);
        add(bar, list);
        refresh();
        // Syöte katsottiin juuri: uudet julkaisut eivät ole enää "uusia"
        unread.markFeedSeen(currentUser.get());
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        UI ui = attachEvent.getUI();
        registration = broadcaster.register(postId -> {
            try {
                ui.access(() -> {
                    refresh();
                    unread.markFeedSeen(currentUser.get());
                    UnreadChanged.fire(ui);
                });
            } catch (UIDetachedException ignored) {
                // selain on jo suljettu
            }
        });
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        if (registration != null) registration.remove();
    }

    private void refresh() {
        list.removeAll();
        AppUser me = currentUser.get();
        List<CatchPost> feed = posts.feed(me, tabs.getSelectedIndex() == 1);
        if (feed.isEmpty()) {
            Paragraph empty = new Paragraph("Ei vielä saaliita. Lisää ensimmäinen tai seuraa muita kalastajia!");
            empty.getStyle().set("grid-column", "1 / -1");
            list.add(empty);
        }
        feed.forEach(p -> list.add(new CatchCard(posts, p, me)));
    }
}
