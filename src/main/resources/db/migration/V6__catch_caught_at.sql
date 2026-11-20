-- Saaliin pyyntiaika: erillään julkaisuhetkestä (created_at), jotta ajan voi muuttaa jälkikäteen.
ALTER TABLE catch_post ADD COLUMN caught_at timestamp(6) with time zone;
UPDATE catch_post SET caught_at = created_at;
ALTER TABLE catch_post ALTER COLUMN caught_at SET NOT NULL;
CREATE INDEX idx_catch_post_caught_at ON catch_post (caught_at DESC);
