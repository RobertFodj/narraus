package com.narraus.ui;

import com.narraus.domain.AppUser;
import com.narraus.domain.CatchPost;
import com.narraus.domain.PostService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;
import jakarta.annotation.security.PermitAll;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Route(value = "", layout = MainLayout.class)
@PageTitle("Saaliit | Narraus")
@PermitAll
public class FeedView extends VerticalLayout {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("d.M.yyyy HH:mm")
            .withZone(ZoneId.systemDefault());

    private final PostService posts;
    private final CurrentUser currentUser;
    private final Tabs tabs = new Tabs(new Tab("Kaikki"), new Tab("Seuraamani"));
    private final VerticalLayout list = new VerticalLayout();

    public FeedView(PostService posts, CurrentUser currentUser) {
        this.posts = posts;
        this.currentUser = currentUser;
        setMaxWidth("42em");
        setAlignItems(FlexComponent.Alignment.STRETCH);
        getStyle().set("margin", "0 auto");

        Button add = new Button("Uusi saalis", VaadinIcon.PLUS.create(),
                e -> new CatchDialog(posts, currentUser, this::refresh).open());
        add.addThemeVariants(ButtonVariant.PRIMARY);
        HorizontalLayout header = new HorizontalLayout(new H2("Saaliit"), add);
        header.setWidthFull();
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        header.setAlignItems(FlexComponent.Alignment.CENTER);

        list.setPadding(false);
        tabs.addSelectedChangeListener(e -> refresh());
        add(header, tabs, list);
        refresh();
    }

    private void refresh() {
        list.removeAll();
        AppUser me = currentUser.get();
        List<CatchPost> feed = posts.feed(me, tabs.getSelectedIndex() == 1);
        if (feed.isEmpty()) {
            list.add(new Paragraph("Ei vielä saaliita. Lisää ensimmäinen tai seuraa muita kalastajia!"));
        }
        feed.forEach(p -> list.add(card(p, me)));
    }

    private VerticalLayout card(CatchPost p, AppUser me) {
        VerticalLayout card = new VerticalLayout();
        card.getStyle().set("border", "1px solid var(--vaadin-border-color, #ddd)")
                .set("border-radius", "var(--vaadin-radius-l, 8px)");
        card.setSpacing(false);

        Span author = new Span(p.getAuthor().getDisplayName());
        author.getStyle().set("font-weight", "600");
        Span time = new Span(TIME.format(p.getCreatedAt()));
        time.getStyle().set("opacity", "0.7").set("font-size", "0.85em");
        HorizontalLayout top = new HorizontalLayout(author, time);
        top.setAlignItems(FlexComponent.Alignment.BASELINE);
        card.add(top);

        if (p.getPhotoFile() != null) {
            String file = p.getPhotoFile();
            Image img = new Image(new StreamResource(file, () -> posts.openPhoto(file)), p.getSpecies());
            img.setWidthFull();
            img.getStyle().set("max-height", "30em").set("object-fit", "cover");
            card.add(img);
        }

        card.add(new H2(p.getSpecies()));
        List<String> stats = new ArrayList<>();
        if (p.getWeightKg() != null) stats.add("Paino " + fmt(p.getWeightKg()) + " kg");
        if (p.getLengthCm() != null) stats.add("Pituus " + fmt(p.getLengthCm()) + " cm");
        if (p.getGirthCm() != null) stats.add("Ympärys " + fmt(p.getGirthCm()) + " cm");
        if (p.getLocation() != null) stats.add("📍 " + p.getLocation());
        if (!stats.isEmpty()) card.add(new Span(String.join("  ·  ", stats)));
        if (p.getDescription() != null) card.add(new Paragraph(p.getDescription()));

        card.add(likeButton(p, me));
        return card;
    }

    private Button likeButton(CatchPost p, AppUser me) {
        Button like = new Button();
        like.addThemeVariants(ButtonVariant.TERTIARY);
        Runnable update = () -> {
            boolean liked = posts.isLiked(p, me);
            like.setIcon((liked ? VaadinIcon.HEART : VaadinIcon.HEART_O).create());
            like.setText(String.valueOf(posts.likeCount(p)));
        };
        like.addClickListener(e -> {
            posts.toggleLike(p, me);
            update.run();
        });
        update.run();
        return like;
    }

    private static String fmt(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.format("%.1f", d).replace('.', ',');
    }
}
