-- Milloin käyttäjä on viimeksi käyttänyt sovellusta asennettuna sovelluksena (PWA) ja milloin selaimessa.
-- Ylläpitäjä näkee näistä, käyttääkö käyttäjä appia, webiä vai molempia.
ALTER TABLE app_user
    ADD COLUMN app_last_used_at timestamp(6) with time zone,
    ADD COLUMN web_last_used_at timestamp(6) with time zone;
