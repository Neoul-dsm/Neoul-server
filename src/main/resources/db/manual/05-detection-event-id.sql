-- Run once after schema alignment, before starting the updated application.
-- Legacy logs retain NULL; new records must supply a UUID through the service.
-- UNIQUE permits multiple legacy NULLs while deduplicating device events.
ALTER TABLE human_log ADD COLUMN event_id VARCHAR(36);
ALTER TABLE human_log ADD CONSTRAINT uk_human_ship_event UNIQUE (ship_id, event_id);
ALTER TABLE marine_life_log ADD COLUMN event_id VARCHAR(36);
ALTER TABLE marine_life_log ADD CONSTRAINT uk_marine_ship_event UNIQUE (ship_id, event_id);
