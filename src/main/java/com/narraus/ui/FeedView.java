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
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.component.UIDetachedException;
import com.vaadin.flow.shared.Registration;
import jakarta.annotation.security.PermitAll;
import org.vaadin.firitin.appframework.MenuItem;
import java.util.List;

@Route(value = "", layout = MainLayout.class)
@PageTitle("Saaliit | Narraus")
@PermitAll
@MenuItem(title = "Saaliit", icon = VaadinIcon.PICTURE, order = 10)
public class FeedView extends VerticalLayout implements BeforeEnterObserver {

    private final PostService posts;
    private final CurrentUser currentUser;
    private final UnreadService unread;
    private final FeedBroadcaster broadcaster;
    private Registration registration;
    private final Tabs tabs = new Tabs(new Tab("Kaikki"), new Tab("Seuraamani"), new Tab("Kartalla"));
    private final Div list = new Div();
    /** "Kartalla"-välilehden sisältö; kartta rakennetaan vasta kun välilehti valitaan. */
    private final Div mapBox = new Div();
    private final MapConfig mapConfig;
    private final GeocodingService geocoder;
    private final com.narraus.domain.AdminPolicy adminPolicy;

    public FeedView(PostService posts, CurrentUser currentUser, UnreadService unread, FeedBroadcaster broadcaster,
                    MapConfig mapConfig, GeocodingService geocoder, com.narraus.domain.AdminPolicy adminPolicy,
                    com.narraus.domain.UserService users, com.narraus.domain.SystemMessageService systemMessages) {
        this.geocoder = geocoder;
        this.adminPolicy = adminPolicy;
        this.posts = posts;
        this.currentUser = currentUser;
        this.unread = unread;
        this.broadcaster = broadcaster;
        this.mapConfig = mapConfig;
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
        tabs.addSelectedChangeListener(e -> {
            loaded = PAGE_SIZE;
            refresh();
        });
        // Otsikko, nappi ja välilehdet ovat "saari", joka pysyy paikallaan kun saaliit vierivät sen alla
        VerticalLayout bar = new VerticalLayout(header, tabs);
        bar.addClassName("island");
        bar.setPadding(false);
        bar.setSpacing(false);
        // Ylläpitäjän pikapainikkeet kotisivulla: ylläpito ja Narraus-viestin lähetys
        Div adminShortcuts = null;
        if (adminPolicy.isAdmin(currentUser.get())) {
            Button adminPage = new Button("Ylläpito", VaadinIcon.COG.create(),
                    e -> UI.getCurrent().navigate(AdminView.class));
            adminPage.addClassName("admin-shortcut-admin");
            Button sendSystem = new Button("Lähetä Narraus-viesti", VaadinIcon.MEGAPHONE.create(),
                    e -> new SystemMessageDialog(users, systemMessages, currentUser.get()).open());
            sendSystem.addClassName("admin-shortcut-send");
            for (Button b : List.of(adminPage, sendSystem)) b.addThemeVariants(ButtonVariant.SMALL);
            adminShortcuts = new Div(adminPage, sendSystem);
            adminShortcuts.addClassName("admin-shortcuts");
        }

        mapBox.addClassName("feed-map");
        mapBox.setWidthFull();
        mapBox.setVisible(false);
        if (adminShortcuts != null) add(bar, adminShortcuts, list, mapBox);
        else add(bar, list, mapBox);
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

    /** Kuinka monta julkaisua ladataan kerralla: ensimmäinen erä riittää täyttämään näkyvän alueen. */
    static final int PAGE_SIZE = 10;

    /** Ladattujen julkaisujen määrä (aina PAGE_SIZE:n monikerta); päivitys säilyttää vieritetyn määrän. */
    private int loaded = PAGE_SIZE;
    private Button more;

    private boolean mapSelected() {
        return tabs.getSelectedIndex() == 2;
    }

    private void refresh() {
        if (mapSelected()) {
            // Kartta-välilehti: kartta rakennetaan vasta nyt; uusi julkaisu ei nollaa kartan näkymää
            list.setVisible(false);
            mapBox.setVisible(true);
            if (mapBox.getComponentCount() == 0) {
                MapView mapView = new MapView(posts, currentUser, mapConfig, true);
                if (mapFocus != null) mapView.focusOn(mapFocus[0], mapFocus[1]);
                mapFocus = null;
                mapBox.add(mapView);
            }
            return;
        }
        mapBox.removeAll();
        mapBox.setVisible(false);
        list.setVisible(true);
        list.removeAll();
        AppUser me = currentUser.get();
        boolean followed = tabs.getSelectedIndex() == 1;
        List<CatchPost> feed = posts.feedPage(me, followed, 0, Math.max(loaded, PAGE_SIZE));
        loaded = Math.max(loaded, PAGE_SIZE);
        if (feed.isEmpty()) {
            Paragraph empty = new Paragraph("Ei vielä saaliita. Lisää ensimmäinen tai seuraa muita kalastajia!");
            empty.getStyle().set("grid-column", "1 / -1");
            list.add(empty);
        }
        shownIds.clear();
        feed.forEach(p -> addCard(p, me));
        addMoreSentinel(feed.size() >= loaded);
    }

    /**
     * Näkymätön "Näytä lisää" -nappi listan lopussa. Selain painaa sitä itse, kun se vieritetään lähelle näkyviin
     * (IntersectionObserver), joten alaspäin olevat julkaisut ladataan vasta kun niitä tarvitaan.
     */
    private void addMoreSentinel(boolean hasMore) {
        if (!hasMore) return;
        more = new Button("Näytä lisää", e -> loadMore());
        more.addThemeVariants(ButtonVariant.TERTIARY);
        more.addClassName("feed-more");
        more.getStyle().set("grid-column", "1 / -1").set("justify-self", "center");
        list.add(more);
        more.getElement().executeJs(
                "const b = this; const o = new IntersectionObserver(es => { if (es[0].isIntersecting) b.click(); },"
                        + " { rootMargin: '600px' }); o.observe(b); b.__feedObserver = o;");
    }

    private void loadMore() {
        if (more == null) return;
        list.remove(more);
        more = null;
        AppUser me = currentUser.get();
        List<CatchPost> next = posts.feedPage(me, tabs.getSelectedIndex() == 1, loaded / PAGE_SIZE, PAGE_SIZE);
        next.forEach(p -> addCard(p, me));
        loaded += PAGE_SIZE;
        addMoreSentinel(next.size() >= PAGE_SIZE);
    }

    private final java.util.Set<Long> shownIds = new java.util.HashSet<>();
    /** Kartan kohdistus (lat, lon), jonka seuraavaksi rakennettava kartta ottaa käyttöön. */
    private double[] mapFocus;

    private void addCard(CatchPost p, AppUser me) {
        shownIds.add(p.getId());
        CatchCard card = new CatchCard(posts, p, me, this::showOnMap);
        // Ylläpitäjä voi muokata ja poistaa kenen tahansa julkaisun
        if (adminPolicy.isAdmin(me)) {
            card.add(PostActions.row(posts, currentUser, mapConfig, geocoder, p, this::refresh, true));
        }
        list.add(card);
    }

    /** Julkaisun paikan painaminen: Kartalla-välilehti avautuu ja zoomaa saaliin paikalle. */
    void showOnMap(CatchPost post) {
        if (!post.hasCoordinates()) return;
        mapFocus = new double[]{post.getLatitude(), post.getLongitude()};
        if (mapSelected()) {
            mapBox.removeAll(); // rakennetaan kartta uudelleen kohdistettuna
            refresh();
        } else {
            tabs.setSelectedIndex(2);
        }
    }

    /**
     * Osoitteen parametrit: {@code ?post=<id>} vierittää listan kyseisen julkaisun kohdalle (kilpailun saaliista),
     * {@code ?map=<id>} avaa kartan ja zoomaa julkaisun paikalle.
     */
    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        var params = event.getLocation().getQueryParameters();
        params.getSingleParameter("map").flatMap(FeedView::parseId).flatMap(posts::find).ifPresent(this::showOnMap);
        params.getSingleParameter("post").flatMap(FeedView::parseId).ifPresent(this::showPost);
    }

    private static java.util.Optional<Long> parseId(String s) {
        try {
            return java.util.Optional.of(Long.parseLong(s));
        } catch (NumberFormatException e) {
            return java.util.Optional.empty();
        }
    }

    /** Näyttää julkaisun Saaliit-listassa: lataa listaa kunnes julkaisu löytyy, vierittää siihen ja korostaa sen. */
    void showPost(Long postId) {
        if (tabs.getSelectedIndex() != 0) {
            loaded = PAGE_SIZE;
            tabs.setSelectedIndex(0);
        }
        int guard = 0;
        while (!shownIds.contains(postId) && more != null && guard++ < 100) loadMore();
        if (!shownIds.contains(postId)) return; // julkaisu on poistettu tai ei löydy
        getElement().executeJs("""
                setTimeout(() => {
                  const el = document.getElementById('post-' + $0);
                  if (!el) return;
                  el.scrollIntoView({ block: 'center', behavior: 'smooth' });
                  el.classList.add('post-focus');
                  setTimeout(() => el.classList.remove('post-focus'), 2500);
                }, 300);""", String.valueOf(postId));
    }
}
