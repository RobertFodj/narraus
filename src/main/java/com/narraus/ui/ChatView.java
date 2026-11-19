package com.narraus.ui;

import com.narraus.domain.AppUser;
import com.narraus.domain.ChatBroadcaster;
import com.narraus.domain.ChatService;
import com.narraus.domain.Conversation;
import com.narraus.domain.UserService;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.messages.MessageInput;
import com.vaadin.flow.component.messages.MessageList;
import com.vaadin.flow.component.messages.MessageListItem;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.shared.Registration;
import jakarta.annotation.security.PermitAll;
import java.util.List;

@Route(value = "chat", layout = MainLayout.class)
@PageTitle("Keskustelut | Narraus")
@PermitAll
public class ChatView extends HorizontalLayout implements BeforeEnterObserver {

    private final ChatService chats;
    private final UserService users;
    private final ChatBroadcaster broadcaster;
    private final AppUser me;

    private final VerticalLayout convList = new VerticalLayout();
    private final H3 title = new H3("Valitse keskustelu");
    private final MessageList messageList = new MessageList();
    private final MessageInput input = new MessageInput();
    private Conversation current;
    private Registration registration;

    public ChatView(ChatService chats, UserService users, ChatBroadcaster broadcaster, CurrentUser currentUser) {
        this.chats = chats;
        this.users = users;
        this.broadcaster = broadcaster;
        this.me = currentUser.get();
        setSizeFull();
        setPadding(true);

        Button newGroup = new Button("Uusi ryhmä", VaadinIcon.GROUP.create(), e -> openGroupDialog());
        newGroup.addThemeVariants(ButtonVariant.PRIMARY, ButtonVariant.SMALL);
        convList.setPadding(false);
        convList.setSpacing(false);
        VerticalLayout left = new VerticalLayout(new H3("Keskustelut"), newGroup, convList);
        left.setWidth("16em");
        left.setPadding(false);
        left.setFlexShrink(0);

        messageList.setSizeFull();
        input.setWidthFull();
        input.setEnabled(false);
        input.addSubmitListener(e -> {
            if (current == null) return;
            chats.send(current, me, e.getValue());
        });
        VerticalLayout right = new VerticalLayout(title, messageList, input);
        right.setPadding(false);
        right.setFlexGrow(1, messageList);

        add(left, right);
        setFlexGrow(1, right);
        refreshConversations();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        event.getLocation().getQueryParameters().getSingleParameter("c").ifPresent(id -> {
            try {
                open(chats.get(Long.valueOf(id), me));
            } catch (NumberFormatException ignored) {
            }
        });
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        UI ui = attachEvent.getUI();
        registration = broadcaster.register(convId -> ui.access(() -> {
            refreshConversations();
            if (current != null && current.getId().equals(convId)) refreshMessages();
        }));
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        if (registration != null) registration.remove();
    }

    private void refreshConversations() {
        convList.removeAll();
        List<Conversation> list = chats.conversationsOf(me);
        if (list.isEmpty()) {
            convList.add(new com.vaadin.flow.component.html.Span("Ei keskusteluja. Aloita viesti Ihmiset-sivulta."));
        }
        for (Conversation c : list) {
            String label = (c.isGroupChat() ? "👥 " : "") + c.titleFor(me);
            Button b = new Button(label, e -> open(c));
            b.addThemeVariants(c.equals(current) ? ButtonVariant.PRIMARY : ButtonVariant.TERTIARY);
            b.setWidthFull();
            b.getStyle().set("justify-content", "flex-start");
            convList.add(b);
        }
    }

    private void open(Conversation c) {
        if (c == null) return;
        current = c;
        title.setText(c.titleFor(me));
        input.setEnabled(true);
        refreshConversations();
        refreshMessages();
    }

    private void refreshMessages() {
        if (current == null) return;
        messageList.setItems(chats.messages(current, me).stream().map(m -> {
            MessageListItem item = new MessageListItem(m.getText(), m.getSentAt(), m.getSender().getDisplayName());
            item.setUserColorIndex((int) (m.getSender().getId() % 7));
            return item;
        }).toList());
    }

    private void openGroupDialog() {
        Dialog d = new Dialog();
        d.setHeaderTitle("Uusi ryhmä");
        TextField name = new TextField("Ryhmän nimi");
        MultiSelectComboBox<AppUser> members = new MultiSelectComboBox<>("Jäsenet");
        members.setItems(users.search("").stream().filter(u -> !u.equals(me)).toList());
        members.setItemLabelGenerator(AppUser::getDisplayName);
        name.setWidthFull();
        members.setWidthFull();
        d.add(new VerticalLayout(name, members));
        Button create = new Button("Luo", e -> {
            try {
                open(chats.createGroup(me, name.getValue(), members.getValue()));
                d.close();
            } catch (IllegalArgumentException ex) {
                Notification.show(ex.getMessage()).addThemeVariants(NotificationVariant.ERROR);
            }
        });
        create.addThemeVariants(ButtonVariant.PRIMARY);
        d.getFooter().add(new Button("Peruuta", e -> d.close()), create);
        d.open();
    }
}
