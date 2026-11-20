package com.narraus.ui;

import com.narraus.domain.AdminPolicy;
import com.narraus.domain.AppUser;
import com.narraus.domain.ChatBroadcaster;
import com.narraus.domain.ChatMessage;
import com.narraus.domain.ChatService;
import com.narraus.domain.Conversation;
import com.narraus.domain.SystemMessage;
import com.narraus.domain.SystemMessageBroadcaster;
import com.narraus.domain.SystemMessageService;
import com.narraus.domain.UnreadService;
import com.narraus.domain.UserService;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.UIDetachedException;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.messages.MessageInput;
import com.vaadin.flow.component.messages.MessageList;
import com.vaadin.flow.component.messages.MessageListItem;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.shared.Registration;
import jakarta.annotation.security.PermitAll;
import org.vaadin.firitin.appframework.MenuItem;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Route(value = "chat", layout = MainLayout.class)
@PageTitle("Keskustelut | Narraus")
@PermitAll
@MenuItem(title = "Keskustelut", icon = VaadinIcon.CHAT, order = 30)
public class ChatView extends HorizontalLayout implements BeforeEnterObserver {

    private final ChatService chats;
    private final UserService users;
    private final ChatBroadcaster broadcaster;
    private final UnreadService unread;
    private final SystemMessageService systemMessages;
    private final SystemMessageBroadcaster systemBroadcaster;
    private final AdminPolicy adminPolicy;
    private final AppUser me;

    private final VerticalLayout convList = new VerticalLayout();
    private final H3 title = new H3("Valitse keskustelu");
    private final Span subtitle = new Span();
    /** Puhelimen sujuvuus: alussa näytetään vain uusimmat viestit, aiemmat haetaan napista. */
    private static final int MESSAGE_PAGE = 30;
    private int visibleMessages = MESSAGE_PAGE;
    private final Button loadEarlier = new Button("Näytä aiemmat viestit", VaadinIcon.ANGLE_UP.create(), e -> {
        visibleMessages += MESSAGE_PAGE;
        refreshMessages();
    });
    private final MessageList messageList = new MessageList();
    private final MessageInput input = new MessageInput();
    /** Puhelimella viestipalkin tilalla: avaa kirjoitusikkunan näytön yläreunaan. */
    private final Button composeBar = new Button("Kirjoita viesti…", VaadinIcon.EDIT.create(), e -> openCompose());
    private Conversation current;
    private final Button editGroup = new Button("Muokkaa ryhmää", VaadinIcon.EDIT.create(), e -> openGroupDialog(current));
    private final Button editMessages = new Button("Muokkaa viestejä", VaadinIcon.PENCIL.create(),
            e -> openEditMessagesDialog());
    private final Button back = new Button("Keskustelut", VaadinIcon.ARROW_LEFT.create(), e -> {
        leaveCurrent();
        refreshConversations();
    });
    private Registration registration;
    private Registration systemRegistration;
    /** Narrauksen viestit ovat auki (oikealla puolella lista Narrauksen viesteistä, ei vastattavissa). */
    private boolean systemOpen;
    /** Narrauksen logo viestien käyttäjäkuvana (PWA-kuvake, julkinen). */
    static final String NARRAUS_LOGO = "/icons/icon-192x192.png";

    /**
     * iOS avaa näppäimistön vain, jos kenttään kohdistetaan suoraan napautuksen aikana (ei palvelinkutsun jälkeen).
     * Siksi napautus kohdistaa heti näkymättömään apukenttään, ja kirjoitusikkunan teksti saa kohdistuksen sen jälkeen;
     * näppäimistö pysyy silloin auki.
     */
    private static final String FOCUS_PROXY_JS = """
            if (this.__proxyBound) return;
            this.__proxyBound = true;
            let proxy = document.getElementById('narraus-focus-proxy');
            if (!proxy) {
              proxy = document.createElement('input');
              proxy.id = 'narraus-focus-proxy';
              proxy.tabIndex = -1;
              proxy.setAttribute('aria-hidden', 'true');
              proxy.style.cssText = 'position:fixed;top:0;left:0;width:1px;height:1px;opacity:0;font-size:16px;'
                + 'border:0;padding:0;pointer-events:none;';
              document.body.appendChild(proxy);
            }
            this.addEventListener('click', () => {
              // Jäädytetään keskustelun korkeus: näppäimistön aukeaminen ei laske viestilistaa uudelleen
              const view = document.querySelector('.chat-view');
              if (view) view.style.height = view.offsetHeight + 'px';
              proxy.focus({ preventScroll: true });
            });
            """;
    private static final String FOCUS_PROXY_CLEANUP_JS = """
            const proxy = document.getElementById('narraus-focus-proxy');
            if (proxy) proxy.remove();
            """;
    /** Vapauttaa jäädytetyn korkeuden, kun kirjoitusikkuna on suljettu. */
    private static final String UNFREEZE_JS = """
            const view = document.querySelector('.chat-view');
            if (view) view.style.removeProperty('height');
            """;

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("d.M. HH:mm")
            .withZone(ZoneId.systemDefault());

    public ChatView(ChatService chats, UserService users, ChatBroadcaster broadcaster, UnreadService unread,
                    CurrentUser currentUser, SystemMessageService systemMessages,
                    SystemMessageBroadcaster systemBroadcaster, AdminPolicy adminPolicy) {
        this.systemMessages = systemMessages;
        this.systemBroadcaster = systemBroadcaster;
        this.adminPolicy = adminPolicy;
        this.chats = chats;
        this.users = users;
        this.broadcaster = broadcaster;
        this.unread = unread;
        this.me = currentUser.get();
        addClassName("chat-view");
        input.setI18n(new com.vaadin.flow.component.messages.MessageInputI18n().setMessage("Kirjoita viesti…").setSend("Lähetä"));
        setSizeFull();
        setPadding(true);

        Button newGroup = new Button("Uusi ryhmä", VaadinIcon.GROUP.create(), e -> openGroupDialog(null));
        newGroup.addThemeVariants(ButtonVariant.PRIMARY, ButtonVariant.SMALL);
        convList.setPadding(false);
        convList.setSpacing(false);
        H3 listTitle = new H3("Keskustelut");
        listTitle.getStyle().set("margin", "0");
        HorizontalLayout listHeader = new HorizontalLayout(listTitle, newGroup);
        listHeader.setWidthFull();
        listHeader.addClassName("island"); // pysyy paikallaan, kun keskustelulista vierii
        listHeader.setAlignItems(FlexComponent.Alignment.CENTER);
        listHeader.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        VerticalLayout left = new VerticalLayout(listHeader, convList);
        left.addClassName("chat-list");
        left.setWidth("19em");
        left.setPadding(false);
        left.setFlexShrink(0);

        // Ei setSizeFull: korkeus tulee tyyleistä (flex), jotta lista vierii eikä kasvata sivua
        messageList.setWidthFull();
        messageList.setMinHeight("0");
        input.setWidthFull();
        input.setEnabled(false);
        input.addSubmitListener(e -> {
            if (current == null) return;
            chats.send(current, me, e.getValue());
        });
        editGroup.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.SMALL);
        editGroup.addClassName("chat-header-btn");
        editMessages.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.SMALL);
        editMessages.addClassName("chat-header-btn");
        back.addClassName("chat-back");
        back.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.SMALL);
        back.addClassName("chat-header-btn");
        subtitle.addClassName("chat-subtitle");
        subtitle.setVisible(false);
        title.getStyle().set("margin", "0");
        VerticalLayout heading = new VerticalLayout(title, subtitle);
        heading.setPadding(false);
        heading.setSpacing(false);
        heading.getStyle().set("min-width", "0");
        HorizontalLayout header = new HorizontalLayout(back, heading, editGroup, editMessages);
        header.setFlexGrow(1, heading);
        header.setWidthFull();
        header.addClassName("island"); // keskustelun otsikko pysyy ylhäällä, viestit vierivät sen alla
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        updateActions();
        composeBar.addClassName("chat-compose-bar");
        composeBar.addClassName("island");
        input.addClassName("island"); // kirjoituskenttä on oma saarensa, joka pysyy paikallaan
        composeBar.setEnabled(false);
        loadEarlier.addClassName("chat-load-earlier");
        loadEarlier.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.SMALL);
        loadEarlier.setVisible(false);
        VerticalLayout right = new VerticalLayout(header, loadEarlier, messageList, input, composeBar);
        right.addClassName("chat-main");
        right.setPadding(false);
        right.setHeightFull();
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
        composeBar.getElement().executeJs(FOCUS_PROXY_JS);
        registration = broadcaster.register(convId -> {
            try {
                ui.access(() -> {
                    boolean isCurrent = current != null && current.getId().equals(convId);
                    if (isCurrent) reloadCurrent(); // ryhmän nimi tai jäsenet ovat voineet muuttua
                    refreshConversations();
                    if (isCurrent && current != null) refreshMessages();
                });
            } catch (UIDetachedException ignored) {
                // selain on jo suljettu
            }
        });
        systemRegistration = systemBroadcaster.register(userId -> {
            if (!me.getId().equals(userId)) return;
            try {
                ui.access(() -> {
                    if (systemOpen) {
                        markSystemRead(); // auki oleva Narraus-viesti on heti luettu
                        refreshSystemMessages();
                    }
                    refreshConversations();
                });
            } catch (UIDetachedException ignored) {
                // selain on jo suljettu
            }
        });
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        detachEvent.getUI().getPage().executeJs(FOCUS_PROXY_CLEANUP_JS);
        if (registration != null) registration.remove();
        if (systemRegistration != null) systemRegistration.remove();
    }

    private void refreshConversations() {
        convList.removeAll();
        List<Conversation> list = chats.conversationsOf(me);
        if (list.isEmpty()) {
            convList.add(new Span("Ei keskusteluja. Aloita viesti Käyttäjät-sivulta."));
        } else {
            // Ryhmät ja yksityisviestit omissa osioissaan, jotta näkee heti kumpi on kumpi
            List<Conversation> groups = list.stream().filter(Conversation::isGroupChat).toList();
            List<Conversation> directs = list.stream().filter(c -> !c.isGroupChat()).toList();
            if (!groups.isEmpty()) {
                convList.add(sectionTitle("Ryhmät", groups.size()));
                groups.forEach(c -> convList.add(conversationItem(c)));
            }
            if (!directs.isEmpty()) {
                convList.add(sectionTitle("Yksityisviestit", directs.size()));
                directs.forEach(c -> convList.add(conversationItem(c)));
            }
        }
        addSystemSection();
    }

    /** Narrauksen viestit omalla otsikollaan, erillään keskusteluista. Näkyy vain, jos viestejä on tullut. */
    private void addSystemSection() {
        if (!systemMessages.hasMessages(me)) return;
        Span heading = new Span("Narrauksen viestit");
        heading.addClassName("chat-section");
        heading.addClassName("chat-section-system");
        convList.add(heading);
        long unreadCount = systemMessages.unreadCount(me);
        Button entry = new Button(SystemMessageService.SENDER_NAME + (unreadCount > 0 ? " (" + unreadCount + ")" : ""),
                VaadinIcon.BELL.create(), e -> openSystem());
        entry.addClassName("chat-item-system");
        entry.addThemeVariants(systemOpen ? ButtonVariant.PRIMARY : ButtonVariant.TERTIARY);
        entry.setWidthFull();
        entry.getStyle().set("justify-content", "flex-start");
        convList.add(entry);
    }

    private static Span sectionTitle(String text, int count) {
        Span s = new Span(text + " (" + count + ")");
        s.addClassName("chat-section");
        return s;
    }

    private Button conversationItem(Conversation c) {
        Button b = new Button(c.titleFor(me), (c.isGroupChat() ? VaadinIcon.USERS : VaadinIcon.USER).create(),
                e -> open(c));
        b.addClassName(c.isGroupChat() ? "chat-item-group" : "chat-item-direct");
        b.addThemeVariants(c.equals(current) ? ButtonVariant.PRIMARY : ButtonVariant.TERTIARY);
        b.setWidthFull();
        b.getStyle().set("justify-content", "flex-start");
        if (c.isGroupChat()) b.setTooltipText(memberSummary(c));
        return b;
    }

    /** "3 jäsentä: Kalle, Maija, Sinä" */
    private String memberSummary(Conversation c) {
        String names = c.getParticipants().stream()
                .map(u -> u.equals(me) ? "Sinä" : u.getDisplayName())
                .sorted().collect(java.util.stream.Collectors.joining(", "));
        int n = c.getParticipants().size();
        return n + (n == 1 ? " jäsen: " : " jäsentä: ") + names;
    }

    /** Otsikko ja sen alla selite: ryhmästä jäsenet, yksityisviestistä merkintä. */
    private void showHeading(Conversation c) {
        title.setText(c == null ? "Valitse keskustelu" : c.titleFor(me));
        subtitle.setVisible(c != null);
        if (c != null) subtitle.setText(c.isGroupChat() ? "Ryhmä · " + memberSummary(c) : "Yksityisviesti");
    }

    private void open(Conversation c) {
        if (c == null) return;
        systemOpen = false;
        if (!c.equals(current)) visibleMessages = MESSAGE_PAGE;
        current = c;
        showHeading(c);
        input.setEnabled(true);
        updateActions();
        refreshConversations();
        refreshMessages();
    }

    /** Hakee keskustelun uudelleen (nimi tai jäsenet voivat olla muuttuneet); jos käyttäjä on poistettu, poistutaan. */
    private void reloadCurrent() {
        Conversation fresh = chats.get(current.getId(), me);
        if (fresh == null) {
            leaveCurrent();
        } else {
            current = fresh;
            showHeading(fresh);
            updateActions();
        }
    }

    private void leaveCurrent() {
        current = null;
        systemOpen = false;
        visibleMessages = MESSAGE_PAGE;
        loadEarlier.setVisible(false);
        showHeading(null);
        input.setEnabled(false);
        messageList.setItems();
        updateActions();
    }

    /** Avaa Narrauksen viestit: lähettäjänä "Narraus" ja logo; viesteihin ei voi vastata. */
    private void openSystem() {
        current = null;
        systemOpen = true;
        visibleMessages = MESSAGE_PAGE;
        loadEarlier.setVisible(false);
        title.setText(SystemMessageService.SENDER_NAME);
        subtitle.setVisible(true);
        subtitle.setText("Viestit Narrauksen ylläpidolta · et voi vastata");
        input.setEnabled(false);
        updateActions();
        markSystemRead();
        refreshConversations();
        refreshSystemMessages();
    }

    private void refreshSystemMessages() {
        List<SystemMessage> shown = systemMessages.messagesFor(me);
        messageList.setItems(shown.stream().map(m -> {
            MessageListItem item = new MessageListItem(m.getText(), m.getCreatedAt(), SystemMessageService.SENDER_NAME,
                    NARRAUS_LOGO);
            item.setUserColorIndex(0);
            return item;
        }).toList());
    }

    private void markSystemRead() {
        if (systemMessages.markAllRead(me)) {
            getUI().ifPresent(UnreadChanged::fire);
        }
    }

    /**
     * Puhelimen kirjoitusikkuna: kenttä on näytön yläreunassa, joten kirjoitettu teksti näkyy aina näppäimistön
     * yläpuolella. Tavallinen viestipalkki on piilossa, kun ikkuna on auki (luokka "composing").
     */
    private void openCompose() {
        if (current == null) return;
        Dialog d = new Dialog();
        d.addClassName("chat-compose");
        d.setHeaderTitle(current.titleFor(me));
        d.setTop("0"); // ikkunan oma reunus antaa noin 8 px tilaa joka puolelle
        d.setLeft("0");
        d.setWidth("calc(100vw - 1rem)");
        d.setMaxWidth("calc(100vw - 1rem)");
        d.setDraggable(false);
        d.setResizable(false);
        d.setCloseOnOutsideClick(false); // kesken jäänyt teksti ei katoa vahingossa

        TextArea text = new TextArea();
        text.setPlaceholder("Kirjoita viesti…");
        text.setWidthFull();
        text.setMinRows(2);
        text.setMaxRows(6);
        text.setMaxLength(2000);
        text.setAutofocus(true);
        d.add(text);

        Button send = new Button("Lähetä", VaadinIcon.PAPERPLANE.create(), e -> {
            if (text.getValue().isBlank()) {
                text.focus();
                return;
            }
            chats.send(current, me, text.getValue());
            d.close();
        });
        send.addThemeVariants(ButtonVariant.PRIMARY);
        d.getFooter().add(new Button("Peruuta", e -> d.close()), send);
        d.addOpenedChangeListener(e -> {
            setClassName("composing", e.isOpened());
            if (!e.isOpened()) UI.getCurrent().getPage().executeJs(UNFREEZE_JS);
        });
        d.open();
        text.focus();
    }

    private void updateActions() {
        // Kapealla näytöllä näytetään joko lista tai avoin keskustelu (CSS käyttää tätä luokkaa)
        setClassName("chat-open", current != null || systemOpen);
        // Narrauksen viesteihin ei voi vastata: kirjoituskenttä piilotetaan tyyleillä (kenttä pysyy pois käytöstä)
        setClassName("chat-readonly", systemOpen);
        editGroup.setVisible(current != null && current.isGroupChat());
        editMessages.setVisible(current != null);
        composeBar.setEnabled(current != null);
    }

    /** Auki oleva keskustelu on luettu: poistaa sen lukemattomat ja päivittää sivupaneelin merkin. */
    private void markCurrentRead() {
        if (current != null && unread.markRead(current, me)) {
            getUI().ifPresent(UnreadChanged::fire);
        }
    }

    private void refreshMessages() {
        if (current == null) return;
        markCurrentRead();
        List<ChatMessage> shown;
        try {
            shown = chats.messages(current, me);
        } catch (SecurityException removed) {
            leaveCurrent(); // käyttäjä on poistettu ryhmästä
            return;
        }
        int from = Math.max(0, shown.size() - visibleMessages);
        loadEarlier.setVisible(from > 0);
        loadEarlier.setText("Näytä aiemmat viestit (" + from + ")");
        messageList.setItems(shown.subList(from, shown.size()).stream().map(m -> {
            String name = m.getSender().getDisplayName() + (m.isEdited() ? " (muokattu)" : "");
            MessageListItem item = new MessageListItem(m.getText(), m.getSentAt(), name);
            item.setUserColorIndex((int) (m.getSender().getId() % 7));
            return item;
        }).toList());
    }

    /** Valitse oma viesti ja muokkaa sen tekstiä. */
    private void openEditMessagesDialog() {
        if (current == null) return;
        Dialog d = new Dialog();
        d.setHeaderTitle("Muokkaa viestejä");
        d.setWidth("min(32em, 95vw)");
        List<ChatMessage> mine = chats.myRecentMessages(current, me);
        if (mine.isEmpty()) {
            d.add(new Span("Et ole vielä lähettänyt viestejä tässä keskustelussa."));
            d.getFooter().add(new Button("Sulje", e -> d.close()));
            d.open();
            return;
        }
        Select<ChatMessage> pick = new Select<>();
        pick.setLabel("Viesti");
        pick.setItems(mine);
        pick.setItemLabelGenerator(m -> TIME.format(m.getSentAt()) + "  " + shorten(m.getText()));
        pick.setWidthFull();
        TextArea text = new TextArea("Viestin teksti");
        text.setMaxLength(2000);
        text.setWidthFull();
        pick.addValueChangeListener(e -> text.setValue(e.getValue() == null ? "" : e.getValue().getText()));
        pick.setValue(mine.get(0));
        d.add(new VerticalLayout(pick, text));

        Button save = new Button("Tallenna", e -> {
            try {
                chats.editMessage(pick.getValue().getId(), me, text.getValue());
                d.close();
                refreshMessages();
            } catch (SecurityException ex) {
                Notification.show("Et voi muokata tätä viestiä").addThemeVariants(NotificationVariant.ERROR);
                d.close();
            } catch (IllegalArgumentException ex) {
                Notification.show(ex.getMessage()).addThemeVariants(NotificationVariant.ERROR);
            }
        });
        save.addThemeVariants(ButtonVariant.PRIMARY);
        d.getFooter().add(new Button("Peruuta", e -> d.close()), save);
        d.open();
    }

    private static String shorten(String text) {
        String oneLine = text.replace('\n', ' ');
        return oneLine.length() > 40 ? oneLine.substring(0, 40) + "…" : oneLine;
    }

    /** Luo uuden ryhmän (existing == null) tai muokkaa olemassa olevan ryhmän nimeä ja jäseniä. */
    private void openGroupDialog(Conversation existing) {
        Dialog d = new Dialog();
        d.setHeaderTitle(existing == null ? "Uusi ryhmä" : "Muokkaa ryhmää");
        TextField name = new TextField("Ryhmän nimi");
        MultiSelectComboBox<AppUser> members = new MultiSelectComboBox<>("Jäsenet");
        // Ladataan haulla ja sivuttain: valinta toimii myös, kun käyttäjiä on satoja (ei vain 50 ensimmäistä)
        members.setItems(
                query -> users.searchOthers(query.getFilter().orElse(""), me, query.getOffset(), query.getLimit())
                        .stream(),
                query -> users.countOthers(query.getFilter().orElse(""), me));
        members.setItemLabelGenerator(AppUser::getDisplayName);
        if (existing != null) {
            name.setValue(existing.getName() == null ? "" : existing.getName());
            Set<AppUser> current = new HashSet<>(existing.getParticipants());
            current.remove(me);
            members.setValue(current);
        }
        name.setWidthFull();
        members.setWidthFull();
        d.add(new VerticalLayout(name, members));
        Button save = new Button(existing == null ? "Luo" : "Tallenna", e -> {
            try {
                Conversation c = existing == null
                        ? chats.createGroup(me, name.getValue(), members.getValue())
                        : chats.updateGroup(existing.getId(), me, name.getValue(), members.getValue());
                open(c);
                d.close();
            } catch (SecurityException ex) {
                Notification.show("Et voi muokata tätä ryhmää").addThemeVariants(NotificationVariant.ERROR);
                d.close();
            } catch (IllegalArgumentException ex) {
                Notification.show(ex.getMessage()).addThemeVariants(NotificationVariant.ERROR);
            }
        });
        save.addThemeVariants(ButtonVariant.PRIMARY);
        d.getFooter().add(new Button("Peruuta", e -> d.close()), save);
        d.open();
    }
}
