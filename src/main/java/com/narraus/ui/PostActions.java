package com.narraus.ui;

import com.narraus.domain.AppUser;
import com.narraus.domain.CatchPost;
import com.narraus.domain.GeocodingService;
import com.narraus.domain.PostService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextField;

/** Julkaisun Muokkaa- ja Poista-napit (oma julkaisu tai ylläpitäjälle kenen tahansa julkaisu). */
final class PostActions {

    private PostActions() {}

    static HorizontalLayout row(PostService posts, CurrentUser currentUser, MapConfig mapConfig,
                                GeocodingService geocoder, CatchPost post, Runnable onChanged) {
        Button edit = new Button("Muokkaa", VaadinIcon.EDIT.create(),
                e -> new CatchDialog(posts, currentUser, mapConfig, geocoder, post, onChanged).open());
        edit.addThemeVariants(ButtonVariant.SMALL);
        edit.addClassName("post-edit");
        Button delete = new Button("Poista", VaadinIcon.TRASH.create(),
                e -> confirmDelete(posts, currentUser.get(), post, onChanged));
        delete.addThemeVariants(ButtonVariant.SMALL, ButtonVariant.ERROR);
        delete.addClassName("post-delete");
        HorizontalLayout row = new HorizontalLayout(edit, delete);
        row.addClassName("post-actions");
        return row;
    }

    static void confirmDelete(PostService posts, AppUser requester, CatchPost post, Runnable onChanged) {
        ConfirmDialog confirm = new ConfirmDialog();
        confirm.setHeader("Poistetaanko julkaisu?");
        confirm.setText("Julkaisu \"" + post.getSpecies() + "\" poistetaan pysyvästi tykkäyksineen ja kuvineen.");
        confirm.setCancelable(true);
        confirm.setCancelText("Peruuta");
        confirm.setConfirmText("Poista");
        confirm.setConfirmButtonTheme("error primary");
        // Ylläpitäjä poistaa toisen julkaisun: syy kirjataan ja näkyy tekijälle, joka voi valittaa
        TextField reason = null;
        if (!post.getAuthor().equals(requester)) {
            reason = new TextField("Syy (näkyy julkaisun tekijälle, valinnainen)");
            reason.setMaxLength(500);
            reason.setWidthFull();
            reason.addClassName("moderation-reason-field");
            confirm.add(reason);
        }
        TextField reasonField = reason;
        confirm.addConfirmListener(e -> {
            try {
                posts.delete(post.getId(), requester, reasonField == null ? null : reasonField.getValue());
            } catch (SecurityException | IllegalArgumentException ex) {
                Notification.show(ex.getMessage()).addThemeVariants(NotificationVariant.ERROR);
            }
            onChanged.run();
        });
        confirm.open();
    }
}
