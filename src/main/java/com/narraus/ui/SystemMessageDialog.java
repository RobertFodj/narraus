package com.narraus.ui;

import com.narraus.domain.AppUser;
import com.narraus.domain.SystemMessageService;
import com.narraus.domain.UserService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import java.util.Set;

/** Ylläpitäjän Narraus-viestin lähetysikkuna (avataan Ylläpito-sivulta). */
class SystemMessageDialog {

    private final UserService users;
    private final SystemMessageService systemMessages;
    private final AppUser me;

    SystemMessageDialog(UserService users, SystemMessageService systemMessages, AppUser me) {
        this.users = users;
        this.systemMessages = systemMessages;
        this.me = me;
    }

    /** Ylläpitäjän lähetysikkuna: kaikille tai valituille käyttäjille. */
    void open() {
        Dialog d = new Dialog();
        d.setHeaderTitle("Lähetä Narraus-viesti");
        d.setWidth("min(34em, 95vw)");

        Checkbox toAll = new Checkbox("Kaikille käyttäjille");
        toAll.setValue(true);
        MultiSelectComboBox<AppUser> recipients = new MultiSelectComboBox<>("Vastaanottajat");
        recipients.setItems(
                query -> users.searchOthers(query.getFilter().orElse(""), me, query.getOffset(), query.getLimit())
                        .stream(),
                query -> users.countOthers(query.getFilter().orElse(""), me));
        recipients.setItemLabelGenerator(AppUser::getDisplayName);
        recipients.setWidthFull();
        recipients.setEnabled(false);
        toAll.addValueChangeListener(e -> recipients.setEnabled(!e.getValue()));

        TextArea text = new TextArea("Viesti");
        text.setMaxLength(SystemMessageService.MAX_LENGTH);
        text.setMinRows(3);
        text.setWidthFull();
        text.setHelperText("Vastaanottaja saa ilmoituksen ja viestin lähettäjältä Narraus. Viestiin ei voi vastata.");
        d.add(new VerticalLayout(toAll, recipients, text));

        Button send = new Button("Lähetä", VaadinIcon.PAPERPLANE.create(), e -> {
            if (toAll.getValue()) {
                ConfirmDialog confirm = new ConfirmDialog();
                confirm.setHeader("Lähetetäänkö kaikille?");
                confirm.setText("Viesti lähetetään " + users.countOthers("", me) + " käyttäjälle.");
                confirm.setCancelable(true);
                confirm.setCancelText("Peruuta");
                confirm.setConfirmText("Lähetä");
                confirm.addConfirmListener(c -> sendSystem(d, true, java.util.Set.of(), text.getValue()));
                confirm.open();
            } else {
                sendSystem(d, false, recipients.getValue(), text.getValue());
            }
        });
        send.addThemeVariants(ButtonVariant.PRIMARY);
        d.getFooter().add(new Button("Peruuta", e -> d.close()), send);
        d.open();
    }

    private void sendSystem(Dialog dialog, boolean toAll, Set<AppUser> chosen, String text) {
        try {
            int count = toAll ? systemMessages.sendToAll(me, text) : systemMessages.send(me, chosen, text);
            dialog.close();
            Notification.show("Viesti lähetetty " + count + (count == 1 ? " käyttäjälle" : " käyttäjälle"))
                    .addThemeVariants(NotificationVariant.SUCCESS);
        } catch (SecurityException ex) {
            Notification.show("Vain ylläpitäjä voi lähettää Narraus-viestejä").addThemeVariants(NotificationVariant.ERROR);
            dialog.close();
        } catch (IllegalArgumentException ex) {
            Notification.show(ex.getMessage()).addThemeVariants(NotificationVariant.ERROR);
        }
    }
}
