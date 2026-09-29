-- Apply once to an existing ships table before starting the updated application.
-- Fresh schemas generated from the updated entities already have this column.
-- Existing ships cannot authenticate until an administrator issues a key.
ALTER TABLE ships ADD COLUMN api_key_hash VARCHAR(64);
