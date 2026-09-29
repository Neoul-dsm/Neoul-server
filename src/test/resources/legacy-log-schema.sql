CREATE TABLE beach (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE boats (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    beach_id BIGINT NOT NULL REFERENCES beach(id),
    code VARCHAR(30) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    latitude DECIMAL(10,7),
    longitude DECIMAL(10,7),
    battery_level DECIMAL(5,2),
    solar_power DECIMAL(10,2),
    speed_knots DOUBLE,
    signal_strength_dbm INTEGER,
    battery_voltage_v DOUBLE,
    last_communication_at TIMESTAMP
);

CREATE TABLE person_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    boat_id BIGINT NOT NULL REFERENCES boats(id),
    image_url VARCHAR(500),
    latitude DECIMAL(10,7) NOT NULL,
    longitude DECIMAL(10,7) NOT NULL,
    detected_at TIMESTAMP NOT NULL
);

CREATE TABLE marine_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    boat_id BIGINT NOT NULL REFERENCES boats(id),
    marine_type VARCHAR(30) NOT NULL,
    count INTEGER NOT NULL,
    detected_at TIMESTAMP NOT NULL
);

CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(254) NOT NULL UNIQUE,
    name VARCHAR(50),
    password VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    beach_id BIGINT REFERENCES beach(id)
);

INSERT INTO beach (id, name) VALUES (100, 'legacy seed');
INSERT INTO boats (id, beach_id, code, name) VALUES (100, 100, 'SEED-100', 'legacy seed ship');
INSERT INTO users (id, email, password, role, beach_id) VALUES (100, 'seed@example.com', 'unused', 'GUARD', 100);
INSERT INTO person_logs (id, boat_id, image_url, latitude, longitude, detected_at)
VALUES (100, 100, 'https://images.example.com/before-migration.jpg', 35.1, 129.1, CURRENT_TIMESTAMP);
INSERT INTO marine_logs (id, boat_id, marine_type, count, detected_at)
VALUES (100, 100, 'jellyfish', 2, CURRENT_TIMESTAMP);
