package com.narraus.ui;

import com.narraus.domain.AppUser;
import com.narraus.domain.CatchPost;
import com.narraus.domain.PostComment;
import com.narraus.domain.PostService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Saaliin kommentit omana ikkunanaan saaliin päällä. Näkyy vasta, kun Kommentoi-nappia painetaan. */
class CommentsDialog extends Dialog {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("d.M.yyyy HH:mm")
            .withZone(ZoneId.systemDefault());

    private final PostService posts;
    private final CatchPost post;
    private final VerticalLayout list = new VerticalLayout();

    CommentsDialog(PostService posts, CatchPost post, AppUser me) {
        this.posts = posts;
        this.post = post;
        setHeaderTitle("Kommentit: " + post.getSpecies());
        setWidth("min(32em, 95vw)");

        list.setPadding(false);
        list.setSpacing(false);
        list.getStyle().set("max-height", "50vh").set("overflow-y", "auto");

        TextArea input = new TextArea("Kirjoita kommentti");
        input.setWidthFull();
        input.setMaxLength(1000);
        Button send = new Button("Lähetä", e -> {
            try {
                posts.addComment(post.getId(), me, input.getValue());
                input.clear();
                refresh();
            } catch (IllegalArgumentException ex) {
                Notification.show(ex.getMessage()).addThemeVariants(NotificationVariant.ERROR);
            }
        });
        send.addThemeVariants(ButtonVariant.PRIMARY);

        add(new VerticalLayout(list, input, send));
        getFooter().add(new Button("Sulje", e -> close()));
        refresh();
    }

    private void refresh() {
        list.removeAll();
        var comments = posts.comments(post);
        if (comments.isEmpty()) list.add(new Paragraph("Ei vielä kommentteja. Kirjoita ensimmäinen!"));
        for (PostComment c : comments) {
            Span who = new Span(c.getAuthor().getDisplayName());
            who.getStyle().set("font-weight", "600");
            Span when = new Span(" · " + TIME.format(c.getCreatedAt()));
            when.getStyle().set("opacity", "0.7").set("font-size", "0.85em");
            Paragraph text = new Paragraph(c.getText());
            text.getStyle().set("margin", "0 0 0.75em 0").set("white-space", "pre-wrap");
            list.add(new com.vaadin.flow.component.html.Div(who, when), text);
        }
    }
}
