package com.neoul.ex.domain.alert.controller;

import com.neoul.ex.domain.alert.dto.DrowningAlertResponse;
import com.neoul.ex.domain.alert.service.DrowningAlertService;
import com.neoul.ex.domain.alert.service.PollingAlertStream;
import com.neoul.ex.domain.user.entity.User;
import com.neoul.ex.domain.user.entity.enums.Role;
import com.neoul.ex.domain.user.repository.UserRepository;
import com.neoul.ex.global.security.JwtProvider;
import com.neoul.ex.domain.beach.entity.Beach;
import com.neoul.ex.domain.beach.repository.BeachRepository;
import com.neoul.ex.domain.ship.entity.Ship;
import com.neoul.ex.domain.ship.repository.ShipRepository;

import static org.assertj.core.api.Assertions.assertThat;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:alert-network;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "alerts.drowning.poll-interval=PT0.05S"
})
@DirtiesContext
class DrowningAlertNetworkTest {
    @Value("${local.server.port}") private int port;
    @Autowired private BeachRepository beaches;
    @Autowired private ShipRepository ships;
    @Autowired private UserRepository users;
    @Autowired private JwtProvider jwt;
    @Autowired private DrowningAlertService alerts;
    @Autowired private PollingAlertStream<com.neoul.ex.domain.alert.dto.DrowningAlertResponse> streams;

    @Test
    void sendsCommittedDetectionOverARealHttpStream() throws Exception {
        var beach = beaches.saveAndFlush(Beach.create("실제 연결 검증 해변"));
        var ship = ships.saveAndFlush(Ship.create("NETWORK-01", "통신 검증 배", beach));
        var guard = users.saveAndFlush(User.createGuard("network@example.com", "unused-password", beach));
        String token = jwt.createAccessToken(guard.getId(), Role.GUARD);
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/alerts/drowning/stream"))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + token).header("Accept", "text/event-stream").GET().build();
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            var executor = Executors.newVirtualThreadPerTaskExecutor();
            try {
                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(response.headers().firstValue("Content-Type").orElseThrow()).contains("text/event-stream");
                var reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8));
                assertThat(executor.submit(() -> readEvent(reader)).get(5, TimeUnit.SECONDS))
                        .contains("event:connected", "DROWNING_ALERT_STREAM_CONNECTED");
                var saved = alerts.record(ship.getId(), "https://images.example.com/drowning.jpg",
                        Instant.parse("2026-09-20T02:00:00Z"), 35.1, 129.1);
                String event = executor.submit(() -> readEvent(reader)).get(5, TimeUnit.SECONDS);
                assertThat(event).contains("event:person-detected", "id:" + saved.detectionId(),
                        "실제 연결 검증 해변", "https://images.example.com/drowning.jpg",
                        "2026-09-20T02:00:00Z", "\"latitude\":35.1", "\"longitude\":129.1");
            } finally {
                response.body().close();
                executor.shutdownNow();
                streams.closeAll();
            }
        }
    }

    private String readEvent(BufferedReader reader) throws Exception {
        StringBuilder event = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            event.append(line).append('\n');
        }
        return event.toString();
    }
}
