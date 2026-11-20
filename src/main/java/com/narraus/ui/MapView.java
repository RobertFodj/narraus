package com.narraus.ui;

import com.narraus.domain.AppUser;
import com.narraus.domain.CatchPost;
import com.narraus.domain.PostService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import java.util.List;
import org.vaadin.addons.maplibre.MapLibre;

/** Kaikki sijainnilliset julkaisut pisteinä kartalla. Pisteestä aukeaa julkaisun tiedot. Kartta alkaa Suomen päältä. */
@Route(value = "map", layout = MainLayout.class)
@PageTitle("Kartta | Narraus")
@PermitAll
public class MapView extends VerticalLayout {

    private final PostService posts;
    private final AppUser me;
    private final MapLibre map;
    private final List<CatchPost> shown;

    public MapView(PostService posts, CurrentUser currentUser, MapConfig mapConfig) {
        this.posts = posts;
        this.me = currentUser.get();
        setSizeFull();
        setPadding(true);

        shown = posts.withLocation();
        String info = shown.isEmpty()
                ? "Kartalla ei ole vielä julkaisuja. Lisää sijainti uutta saalista julkaistessasi."
                : shown.size() + (shown.size() == 1 ? " julkaisu kartalla" : " julkaisua kartalla");

        map = mapConfig.newMap();
        map.setWidthFull();
        map.setHeight("calc(100dvh - 9rem)");
        map.setMinHeight("20em");
        for (CatchPost p : shown) {
            map.addMarker(p.getLongitude(), p.getLatitude()).addClickListener(() -> openDetails(p));
        }

        add(new H2("Kartta"), new Span(info), map);
    }

    /** Avaa pisteen julkaisun tiedot. */
    void openDetails(CatchPost post) {
        Dialog d = new Dialog();
        d.setHeaderTitle(post.getSpecies());
        d.setWidth("min(36em, 95vw)");
        d.add(new CatchCard(posts, post, me));
        d.getFooter().add(new Button("Sulje", e -> d.close()));
        d.open();
    }

    int markerCount() {
        return shown.size();
    }

    List<CatchPost> shownPosts() {
        return shown;
    }

    MapLibre map() {
        return map;
    }
}
