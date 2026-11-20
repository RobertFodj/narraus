package com.narraus.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.narraus.NarrausTest;
import com.narraus.domain.AppUser;
import com.narraus.domain.CatchPost;
import com.narraus.domain.PostService;
import com.narraus.domain.UserService;
import com.vaadin.browserless.BrowserlessUIContext;
import com.vaadin.browserless.SecuredBrowserlessApplicationContext;
import com.vaadin.flow.component.html.H2;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;

/** Kalalajit käännetään katsojan kielelle; tallennetaan suomenkielinen nimi, ja valikko on kielen mukainen. */
@NarrausTest
class SpeciesTest {

    @Autowired
    private SecuredBrowserlessApplicationContext<Authentication> app;
    @Autowired
    private UserService users;
    @Autowired
    private PostService posts;

    @Test
    void everyMenuSpecies_hasADistinctNameInEveryLanguage() {
        assertEquals(16, Species.all().size());
        for (Language l : Language.values()) {
            var names = Species.all().stream().map(s -> Species.display(s, l)).toList();
            assertTrue(names.stream().noneMatch(n -> n == null || n.isBlank()), l.name());
            if (l != Language.NO && l != Language.SV) continue;
        }
        assertEquals("Gädda", Species.display("Hauki", Language.SV));
        assertEquals("Gjedde", Species.display("Hauki", Language.NO));
        assertEquals("Pike", Species.display("Hauki", Language.EN));
        assertEquals("Hauki", Species.display("Hauki", Language.FI));
        assertEquals("Grayling", Species.display("Harjus", Language.EN));
        assertEquals("Regnbåge", Species.display("Kirjolohi", Language.SV));
    }

    @Test
    void customSpecies_isShownAsWritten_inEveryLanguage() {
        for (Language l : Language.values()) assertEquals("Kiiski", Species.display("Kiiski", l));
        assertEquals(null, Species.display(null, Language.EN));
    }

    @Test
    void typedName_isRecognisedInAnyLanguage_andStoredInFinnish() {
        assertEquals(Optional.of("Hauki"), Species.canonical("gädda"));
        assertEquals(Optional.of("Hauki"), Species.canonical(" Pike "));
        assertEquals(Optional.of("Hauki"), Species.canonical("GJEDDE"));
        assertEquals(Optional.of("Ahven"), Species.canonical("Perch"));
        assertEquals(Optional.empty(), Species.canonical("Kiiski"));
        assertEquals(Optional.empty(), Species.canonical(" "));
    }

    private static String heading(BrowserlessUIContext window) {
        return window.find(H2.class).all().stream().map(H2::getText)
                .filter(t -> List.of("Hauki", "Gädda", "Pike", "Gjedde").contains(t)).findFirst().orElse("-");
    }

    @Test
    void postCard_showsTheSpeciesInTheViewersLanguage_whileTheStoredValueStaysFinnish() {
        AppUser me = TestData.newUser(users, "Minä");
        CatchPost post = posts.create(me, "Hauki", null, null, null, null, null, null, null);

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.navigate(FeedView.class);
            assertEquals("Hauki", heading(window), "Suomeksi sellaisenaan");

            window.getUI().setLocale(Language.SV.locale());
            window.navigate(UsersView.class); // sama osoite ei rakenna näkymää uudelleen
            window.navigate(FeedView.class);
            assertEquals("Gädda", heading(window));

            window.getUI().setLocale(Language.EN.locale());
            window.navigate(UsersView.class);
            window.navigate(FeedView.class);
            assertEquals("Pike", heading(window));
        }
        assertEquals("Hauki", posts.byAuthor(me).get(0).getSpecies(), "Tietokannassa suomenkielinen nimi");
        assertEquals(post.getId(), posts.byAuthor(me).get(0).getId());
    }

    @Test
    void dialogMenu_listsTheSpeciesInTheUsersLanguage_andSavingKeepsTheFinnishName() {
        AppUser me = TestData.newUser(users, "Minä");

        try (var user = app.newUser(me.getUsername(), "USER")) {
            var window = user.newWindow();
            window.getUI().setLocale(Language.SV.locale());
            window.navigate(FeedView.class);
            window.findButton().withText("Uusi saalis").click();

            var species = TestData.species(window);
            var labels = species.getListDataView().getItems().map(species.getItemLabelGenerator()::apply).toList();
            assertEquals("Aborre", labels.get(0));
            assertEquals("Gädda", labels.get(1));
            assertTrue(labels.contains("Harr"));

            species.setValue("Hauki"); // valikon arvo on suomenkielinen
            assertEquals("Gädda", species.getItemLabelGenerator().apply(species.getValue()));
            window.findButton().withText("Julkaise").click();

            assertEquals("Hauki", posts.byAuthor(me).get(0).getSpecies());
        }
    }
}
