-- Muokatun viestin merkintä: milloin viestiä on viimeksi muokattu (NULL = ei muokattu).
ALTER TABLE chat_message ADD COLUMN edited_at timestamp(6) with time zone;
