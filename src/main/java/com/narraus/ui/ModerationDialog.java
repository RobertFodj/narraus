package com.narraus.ui;

import com.narraus.domain.AppUser;
import com.narraus.domain.ModerationAction;
import com.narraus.domain.ModerationAppeal;
import com.narraus.domain.ModerationKind;
import com.narraus.domain.ModerationService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Ylläpitäjän päätös (julkaisun poisto tai muokkaus) käyttäjälle: näyttää, mitä tehtiin ja miksi, sekä lomakkeen,
 * jolla käyttäjä voi valittaa tai antaa palautetta.
 */
class ModerationDialog extends Dialog {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("d.M.yyyy HH:mm")
            .withZone(ZoneId.systemDefault());

    private final ModerationService moderation;
    private final AppUser me;
    private final ModerationAction action;
    private final VerticalLayout sent = new VerticalLayout();

    ModerationDialog(ModerationService moderation, AppUser me, ModerationAction action) {
        this.moderation = moderation;
        this.me = me;
        this.action = action;
        addClassName("moderation-dialog");
        setHeaderTitle("Ylläpitäjän päätös");
        setWidth("min(32em, 95vw)");

        VerticalLayout body = new VerticalLayout();
        body.setPadding(false);
        String species = Species.display(action.getSpecies());
        body.add(new Paragraph(action.getKind() == ModerationKind.DELETED
                ? "Ylläpitäjä poisti julkaisusi: " + species
                : "Ylläpitäjä muokkasi julkaisuasi: " + species));
        Span when = new Span(TIME.format(action.getCreatedAt()));
        when.getStyle().set("opacity", "0.7").set("font-size", "0.85em");
        body.add(when);
        if (action.getDescription() != null) body.add(new Paragraph("Julkaisun kuvaus: " + action.getDescription()));
        if (action.getReason() != null) {
            Paragraph reason = new Paragraph("Syy: " + action.getReason());
            reason.addClassName("moderation-reason");
            body.add(reason);
        }

        sent.setPadding(false);
        sent.setSpacing(false);
        body.add(sent);
        showSent();

        TextArea text = new TextArea("Valitus tai palaute");
        text.setMaxLength(ModerationService.MAX_APPEAL_LENGTH);
        text.setMinRows(3);
        text.setWidthFull();
        text.setHelperText("Ylläpitäjä näkee viestin. Voit kertoa, miksi päätös on mielestäsi väärä, tai antaa muuta palautetta.");
        body.add(text);
        add(body);

        Button send = new Button("Lähetä", VaadinIcon.PAPERPLANE.create(), e -> {
            try {
                moderation.appeal(action.getId(), me, text.getValue());
            } catch (IllegalArgumentException ex) {
                Notification.show(ex.getMessage()).addThemeVariants(NotificationVariant.ERROR);
                return;
            } catch (SecurityException ex) {
                Notification.show("Ei oikeutta").addThemeVariants(NotificationVariant.ERROR);
                return;
            }
            text.clear();
            showSent();
            Notification.show("Kiitos, viesti lähetetty ylläpitäjälle").addThemeVariants(NotificationVariant.SUCCESS);
        });
        send.addThemeVariants(ButtonVariant.PRIMARY);
        send.addClassName("moderation-send");
        getFooter().add(new Button("Sulje", e -> close()), send);
    }

    /** Aiemmin lähetetyt viestit tästä päätöksestä. */
    private void showSent() {
        sent.removeAll();
        var appeals = moderation.appealsOf(action);
        if (appeals.isEmpty()) return;
        sent.add(new H4("Lähettämäsi viestit"));
        for (ModerationAppeal a : appeals) {
            Div row = new Div(new Span(a.getText()), new Span(" · " + TIME.format(a.getCreatedAt())));
            row.addClassName("moderation-sent");
            row.getStyle().set("padding", "4px 0");
            sent.add(row);
        }
    }
}
