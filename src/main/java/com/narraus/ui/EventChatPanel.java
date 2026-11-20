package com.narraus.ui;

import com.narraus.domain.AppUser;
import com.narraus.domain.EventService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.messages.MessageInput;
import com.vaadin.flow.component.messages.MessageList;
import com.vaadin.flow.component.messages.MessageListItem;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;

/**
 * Kilpailun keskustelu. Kaikki kirjautuneet voivat kirjoittaa. Leveällä näytöllä on tavallinen viestikenttä;
 * puhelimella "Kirjoita viesti" -nappi avaa kirjoitusikkunan näytön yläreunaan (sama tapa kuin keskusteluissa).
 */
class EventChatPanel extends VerticalLayout {

    private final EventService events;
    private final Long eventId;
    private final AppUser me;
    private final MessageList list = new MessageList();

    EventChatPanel(EventService events, Long eventId, AppUser me) {
        this.events = events;
        this.eventId = eventId;
        this.me = me;
        addClassName("event-chat");
        setPadding(false);

        list.addClassName("event-chat-list");
        list.setWidthFull();

        MessageInput input = new MessageInput();
        input.addClassName("event-chat-input");
        input.setWidthFull();
        input.addSubmitListener(e -> send(e.getValue()));

        Button compose = new Button("Kirjoita viesti…", VaadinIcon.EDIT.create(), e -> openCompose());
        compose.addClassName("event-compose");

        add(list, input, compose);
        refresh();
    }

    void refresh() {
        list.setItems(events.messages(eventId).stream().map(m -> {
            MessageListItem item = new MessageListItem(m.getText(), m.getSentAt(), m.getSender().getDisplayName());
            item.setUserColorIndex((int) (m.getSender().getId() % 7));
            return item;
        }).toList());
    }

    private boolean send(String text) {
        try {
            events.postMessage(eventId, me, text);
            return true;
        } catch (IllegalArgumentException ex) {
            Notification.show(ex.getMessage()).addThemeVariants(NotificationVariant.ERROR);
            return false;
        }
    }

    private void openCompose() {
        Dialog d = new Dialog();
        d.addClassName("chat-compose");
        d.setHeaderTitle("Kilpailun keskustelu");
        d.setTop("0");
        d.setLeft("0");
        d.setWidth("calc(100vw - 1rem)");
        d.setMaxWidth("calc(100vw - 1rem)");
        d.setDraggable(false);
        d.setResizable(false);
        d.setCloseOnOutsideClick(false);

        TextArea text = new TextArea();
        text.setPlaceholder("Kirjoita viesti…");
        text.setWidthFull();
        text.setMinRows(2);
        text.setMaxRows(6);
        text.setMaxLength(1000);
        text.setAutofocus(true);
        d.add(text);

        Button sendButton = new Button("Lähetä", VaadinIcon.PAPERPLANE.create(), e -> {
            if (text.getValue().isBlank()) {
                text.focus();
                return;
            }
            if (send(text.getValue())) d.close();
        });
        sendButton.addThemeVariants(ButtonVariant.PRIMARY);
        d.getFooter().add(new Button("Peruuta", e -> d.close()), sendButton);
        d.open();
        text.focus();
    }
}
