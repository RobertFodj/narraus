-- "Pysy kirjautuneena": Spring Securityn pysyvät kirjautumistunnisteet. Yksi rivi per selain/laite (series).
-- Tunniste kierrätetään jokaisella käytöllä; varastettu ja uudelleenkäytetty tunniste havaitaan ja sarja poistetaan.
CREATE TABLE persistent_logins (
    series    varchar(64) PRIMARY KEY,
    username  varchar(255) NOT NULL,
    token     varchar(64)  NOT NULL,
    last_used timestamp(6) with time zone NOT NULL
);

CREATE INDEX idx_persistent_logins_username ON persistent_logins (username);
