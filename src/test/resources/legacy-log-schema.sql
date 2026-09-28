CREATE TABLE beach (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE boats (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    beach_id BIGINT NOT NULL REFERENCES beach(id),
    latitude DECIMAL(10,7),
    longitude DECIMAL(10,7),
    battery_level DECIMAL(5,2),
    solar_power DECIMAL(10,2),
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
