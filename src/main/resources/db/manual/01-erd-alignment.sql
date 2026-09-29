-- Run once against the previous schema while the application is stopped.
-- Back up the database first. MySQL DDL is not transactionally reversible.
ALTER TABLE beach RENAME TO beaches;
ALTER TABLE boats RENAME TO ships;
ALTER TABLE person_logs RENAME TO human_log;
ALTER TABLE marine_logs RENAME TO marine_life_log;
ALTER TABLE human_log RENAME COLUMN boat_id TO ship_id;
ALTER TABLE marine_life_log RENAME COLUMN boat_id TO ship_id;

ALTER TABLE beaches ADD COLUMN latitude DECIMAL(10,7);
ALTER TABLE beaches ADD COLUMN longitude DECIMAL(10,7);
ALTER TABLE human_log ADD COLUMN ai_result BIT;

CREATE TABLE star_beaches (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    beach_id BIGINT NOT NULL,
    CONSTRAINT uk_star_beach_user_beach UNIQUE (user_id, beach_id),
    CONSTRAINT fk_star_beach_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_star_beach_beach FOREIGN KEY (beach_id) REFERENCES beaches(id)
);

INSERT INTO star_beaches (user_id, beach_id)
SELECT id, beach_id FROM users WHERE beach_id IS NOT NULL;

CREATE TABLE solar_generation_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ship_id BIGINT NOT NULL,
    period_started_at TIMESTAMP(6) NOT NULL,
    received_at TIMESTAMP(6) NOT NULL,
    energy_wh DECIMAL(14,3) NOT NULL,
    CONSTRAINT uk_solar_ship_period UNIQUE (ship_id, period_started_at),
    CONSTRAINT fk_solar_ship FOREIGN KEY (ship_id) REFERENCES ships(id),
    CONSTRAINT ck_solar_energy CHECK (energy_wh >= 0)
);

-- Keep users.beach_id and previous environmental columns for rollback inspection.
-- The application now reads assignments exclusively from star_beaches.
