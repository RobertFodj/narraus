-- Kilpailun nimen ja kuvauksen käännökset (valinnaisia; puuttuessa näytetään suomenkielinen teksti)
ALTER TABLE competition_event ADD COLUMN name_sv varchar(120);
ALTER TABLE competition_event ADD COLUMN name_no varchar(120);
ALTER TABLE competition_event ADD COLUMN name_en varchar(120);
ALTER TABLE competition_event ADD COLUMN description_sv varchar(2000);
ALTER TABLE competition_event ADD COLUMN description_no varchar(2000);
ALTER TABLE competition_event ADD COLUMN description_en varchar(2000);
