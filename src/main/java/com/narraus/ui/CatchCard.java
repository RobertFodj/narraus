package com.narraus.ui;

import com.narraus.domain.AppUser;
import com.narraus.domain.CatchPost;
import com.narraus.domain.PostService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.server.StreamResource;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Yhden saaliin kortti. Käytetään syötteessä ja profiilisivuilla. Näyttää vain julkista tietoa (näyttönimi, ei tunnusta). */
class CatchCard extends VerticalLayout {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("d.M.yyyy HH:mm")
            .withZone(ZoneId.systemDefault());

    CatchCard(PostService posts, CatchPost p, AppUser me) {
        addClassName("catch-card");
        getStyle().set("min-width", "0"); // jotta kortti mahtuu ruudukon sarakkeeseen
        setSpacing(false);

        Span author = new Span(p.getAuthor().getDisplayName());
        author.getStyle().set("font-weight", "600");
        Span time = new Span(TIME.format(p.getCaughtAt()));
        time.getStyle().set("opacity", "0.7").set("font-size", "0.85em");
        HorizontalLayout top = new HorizontalLayout(author, time);
        top.setAlignItems(FlexComponent.Alignment.BASELINE);
        add(top);

        if (p.getPhotoFile() != null) {
            String file = p.getPhotoFile();
            Image img = new Image(new StreamResource(file, () -> posts.openThumbnail(file)), p.getSpecies());
            img.getElement().setAttribute("loading", "lazy"); // kuvat ladataan vasta kun ne tulevat näkyviin
            // Kuva säilyttää oman muotonsa (ei rajausta), mutta näkyy pienenä; klikkaus avaa sen isona
            img.getStyle().set("width", "auto").set("height", "auto")
                    .set("max-width", "100%").set("max-height", "10em")
                    .set("object-fit", "contain").set("align-self", "flex-start").set("cursor", "zoom-in")
                    .set("border-radius", "var(--vaadin-radius-m, 6px)");
            img.addClickListener(e -> enlarge(posts, file, p.getSpecies()));
            add(img);
        }

        add(new H2(p.getSpecies()));
        List<String> stats = new ArrayList<>();
        if (p.getWeightKg() != null) stats.add("Paino " + fmt(p.getWeightKg()) + " kg");
        if (p.getLengthCm() != null) stats.add("Pituus " + fmt(p.getLengthCm()) + " cm");
        if (p.getGirthCm() != null) stats.add("Ympärys " + fmt(p.getGirthCm()) + " cm");
        if (p.getLocation() != null) stats.add("📍 " + p.getLocation());
        if (!stats.isEmpty()) add(new Span(String.join("  ·  ", stats)));
        if (p.getDescription() != null) add(new Paragraph(p.getDescription()));

        Button like = likeButton(posts, p, me);
        like.addClassName("catch-like");
        add(like);
    }

    /** Kuvan klikkaus avaa kuvan isona; uusi klikkaus tai ulkopuolelle painaminen sulkee sen. */
    private static void enlarge(PostService posts, String file, String alt) {
        Dialog dialog = new Dialog();
        dialog.addClassName("photo-dialog");
        dialog.setWidth("min(60em, 95vw)");
        Image big = new Image(new StreamResource(file, () -> posts.openPhoto(file)), alt);
        big.setWidthFull();
        big.getStyle().set("max-height", "85vh").set("object-fit", "contain").set("cursor", "zoom-out");
        big.addClickListener(e -> dialog.close());
        dialog.add(big);
        dialog.open();
    }

    private static Button likeButton(PostService posts, CatchPost p, AppUser me) {
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
