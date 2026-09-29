-- Apply once to an existing ships table that contains these legacy columns.
-- Back up the database and stop the application before applying this script.
-- The five removed measurements are deleted permanently by this migration.
-- Fresh databases generated from the current entities do not need this script.
ALTER TABLE ships DROP COLUMN speed_knots;
ALTER TABLE ships DROP COLUMN battery_voltage_v;
ALTER TABLE ships DROP COLUMN signal_strength_dbm;
ALTER TABLE ships DROP COLUMN battery_level;
ALTER TABLE ships DROP COLUMN solar_power;
