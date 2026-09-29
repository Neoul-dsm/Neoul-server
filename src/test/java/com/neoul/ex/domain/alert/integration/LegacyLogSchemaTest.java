package com.neoul.ex.domain.alert.integration;

import com.neoul.ex.domain.alert.service.DrowningAlertService;
import com.neoul.ex.domain.alert.service.MarineLifeAlertService;
import com.neoul.ex.domain.user.entity.User;
import com.neoul.ex.domain.user.entity.enums.Role;
import com.neoul.ex.domain.user.repository.UserRepository;
import com.neoul.ex.global.security.AccessTokenPrincipal;
import com.neoul.ex.domain.beach.entity.Beach;
import com.neoul.ex.domain.beach.repository.BeachRepository;
import com.neoul.ex.domain.ship.entity.Ship;
import com.neoul.ex.domain.ship.repository.ShipRepository;
import com.neoul.ex.domain.ship.service.ShipService;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:legacy-log-schema;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:legacy-log-schema.sql,classpath:db/manual/01-erd-alignment.sql,classpath:db/manual/03-remove-unused-ship-measurements.sql,classpath:db/manual/05-detection-event-id.sql,classpath:db/manual/07-ship-api-key.sql",
        "spring.jpa.hibernate.ddl-auto=update"
})
@DirtiesContext
class LegacyLogSchemaTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired BeachRepository beaches;
    @Autowired ShipRepository ships;
    @Autowired UserRepository users;
    @Autowired ShipService shipService;
    @Autowired DrowningAlertService drowning;
    @Autowired MarineLifeAlertService marine;

    @Test
    void migratesExistingAssignmentsAndLogsWithoutCreatingDuplicateTables() {
        assertThat(jdbc.queryForObject("SELECT beach_id FROM star_beaches WHERE user_id = 100", Long.class)).isEqualTo(100L);
        assertThat(jdbc.queryForObject("SELECT image_url FROM human_log WHERE id = 100", String.class)).isEqualTo("https://images.example.com/before-migration.jpg");
        assertThat(jdbc.queryForObject("SELECT count FROM marine_life_log WHERE id = 100", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT event_id FROM human_log WHERE id = 100", String.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT event_id FROM marine_life_log WHERE id = 100", String.class)).isNull();
        var beach = beaches.saveAndFlush(Beach.create("기존 로그 해변"));
        var otherBeach = beaches.saveAndFlush(Beach.create("다른 로그 해변"));
        var ship = ships.saveAndFlush(Ship.create("LEGACY-01", "기존 배", beach));
        var otherShip = ships.saveAndFlush(Ship.create("LEGACY-02", "다른 배", otherBeach));
        var guard = users.saveAndFlush(User.createGuard("legacy@example.com", "unused", beach));
        var principal = new AccessTokenPrincipal(guard.getId(), Role.GUARD, "legacy-test", Instant.now().plusSeconds(60));

        jdbc.update("UPDATE ships SET last_communication_at = CURRENT_TIMESTAMP WHERE id = ?", ship.getId());
        jdbc.update("INSERT INTO human_log (ship_id, image_url, latitude, longitude, detected_at) VALUES (?, ?, 35.1587, 129.1604, CURRENT_TIMESTAMP)",
                ship.getId(), "https://images.example.com/legacy.jpg");
        jdbc.update("INSERT INTO marine_life_log (ship_id, marine_type, count, detected_at) VALUES (?, '해파리', 3, CURRENT_TIMESTAMP)", ship.getId());
        marine.record(otherShip.getId(), java.util.UUID.randomUUID().toString(), "상어", 1, Instant.now());

        var people = drowning.getNext(principal, beach.getId(), 0);
        var creatures = marine.getNext(principal, beach.getId(), 0);
        assertThat(people).singleElement().satisfies(log -> {
            assertThat(log.imageUrl()).isEqualTo("https://images.example.com/legacy.jpg");
            assertThat(log.beachId()).isEqualTo(beach.getId());
        });
        assertThat(creatures).singleElement().satisfies(log -> assertThat(log.species()).isEqualTo("해파리"));
        assertThat(shipService.getConnection(ship.getId(), principal).lastReceivedAt()).isNotNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE LOWER(table_name) = 'ships' AND LOWER(column_name) IN ('speed_knots', 'battery_voltage_v', 'signal_strength_dbm', 'battery_level', 'solar_power')", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE LOWER(table_name) = 'solar_generation_logs'", Integer.class)).isEqualTo(1);

        var savedPerson = drowning.record(ship.getId(), java.util.UUID.randomUUID().toString(), "https://images.example.com/new.jpg", Instant.now(), 35.1, 129.1);
        var savedMarine = marine.record(ship.getId(), java.util.UUID.randomUUID().toString(), "해파리", 5, Instant.now());
        assertThat(jdbc.queryForObject("SELECT ship_id FROM human_log WHERE id = ?", Long.class, savedPerson.detectionId())).isEqualTo(ship.getId());
        assertThat(jdbc.queryForObject("SELECT count FROM marine_life_log WHERE id = ?", Integer.class, savedMarine.detectionId())).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE LOWER(table_name) IN ('beach', 'boats', 'person_logs', 'marine_logs')", Integer.class)).isZero();
    }
}
