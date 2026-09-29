package com.neoul.ex.domain.alert.controller;

import com.neoul.ex.domain.alert.dto.DrowningAlertResponse;
import com.neoul.ex.domain.alert.repository.HumanLogRepository;
import com.neoul.ex.domain.alert.service.DrowningAlertService;
import com.neoul.ex.domain.alert.service.PollingAlertStream;
import com.neoul.ex.domain.user.entity.User;
import com.neoul.ex.domain.user.entity.enums.Role;
import com.neoul.ex.domain.auth.repository.RevokedAccessTokenRepository;
import com.neoul.ex.domain.user.repository.UserRepository;
import com.neoul.ex.global.security.JwtProvider;
import com.neoul.ex.domain.beach.entity.Beach;
import com.neoul.ex.domain.beach.repository.BeachRepository;
import com.neoul.ex.global.testutil.CommonResponseAssertions;
import com.neoul.ex.domain.ship.entity.Ship;
import com.neoul.ex.domain.ship.repository.ShipRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
class DrowningAlertApiTest {
    private static final String URL = "/alerts/drowning/stream";
    private static final Instant DETECTED_AT = Instant.parse("2026-09-20T01:23:45Z");
    @Autowired private MockMvc mvc;
    @Autowired private DrowningAlertService alerts;
    @Autowired private PollingAlertStream<com.neoul.ex.domain.alert.dto.DrowningAlertResponse> streams;
    @Autowired private HumanLogRepository detections;
    @Autowired private BeachRepository beaches;
    @Autowired private ShipRepository ships;
    @Autowired private UserRepository users;
    @Autowired private RevokedAccessTokenRepository revokedTokens;
    @Autowired private JwtProvider jwt;
    @Autowired private JsonMapper mapper;
    @MockitoBean(name = "drowningAlertScheduler") private TaskScheduler scheduler;
    @MockitoBean private Clock clock;
    private final List<Runnable> polls = new ArrayList<>();
    private final List<ScheduledFuture<?>> tasks = new ArrayList<>();
    private Beach beach;
    private Beach otherBeach;
    private Ship ownShip;
    private Ship otherShip;
    private User guard;
    private String token;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(Instant.now());
        polls.clear();
        tasks.clear();
        when(scheduler.scheduleWithFixedDelay(any(Runnable.class), any(Instant.class), any(Duration.class)))
                .thenAnswer(invocation -> {
                    polls.add(invocation.getArgument(0));
                    ScheduledFuture<?> task = mock(ScheduledFuture.class);
                    tasks.add(task);
                    return task;
                });
        beach = beaches.saveAndFlush(Beach.create("익수자 검증 해변"));
        otherBeach = beaches.saveAndFlush(Beach.create("다른 검증 해변"));
        ownShip = ships.saveAndFlush(Ship.create("OWN-01", "담당 배", beach));
        otherShip = ships.saveAndFlush(Ship.create("OTHER-01", "다른 배", otherBeach));
        guard = users.saveAndFlush(User.createGuard("alert-guard@example.com", "unused-password", beach));
        token = jwt.createAccessToken(guard.getId(), Role.GUARD);
    }

    @AfterEach
    void cleanUp() {
        streams.closeAll();
        detections.deleteAll();
        revokedTokens.deleteAll();
        users.deleteById(guard.getId());
        ships.deleteById(ownShip.getId());
        ships.deleteById(otherShip.getId());
        beaches.deleteById(beach.getId());
        beaches.deleteById(otherBeach.getId());
    }

    @Test
    void retriedDeviceEventDoesNotSendAnotherAlert() throws Exception {
        var connection = connect(null);
        String eventId = java.util.UUID.randomUUID().toString();
        var first = alerts.record(ownShip.getId(), eventId, "https://images.example.com/retry.jpg", DETECTED_AT, 35.1, 129.1);
        polls.getFirst().run();
        var retry = alerts.record(ownShip.getId(), eventId, "https://images.example.com/retry.jpg", DETECTED_AT, 35.1, 129.1);
        polls.getFirst().run();
        assertThat(retry.detectionId()).isEqualTo(first.detectionId());
        assertThat(payloads(connection)).hasSize(2);
        assertThat(detections.count()).isEqualTo(1);
        streams.closeAll();
        mvc.perform(asyncDispatch(connection)).andExpect(status().isOk());
    }

    @Test
    void sendsNewDetectionWithPhotoTimeGpsAndCommonEnvelopeOnlyForOwnBeach() throws Exception {
        MvcResult connection = connect(null);
        assertThat(payloads(connection)).hasSize(1);
        record(otherBeach.getId(), "other.jpg");
        var saved = record(beach.getId(), "person.jpg");
        polls.getFirst().run();
        List<JsonNode> data = payloads(connection);
        assertThat(data).hasSize(2);
        JsonNode alert = data.get(1);
        assertEnvelope(alert, "DROWNING_DETECTED");
        assertThat(alert.get("data").get("detectionId").longValue()).isEqualTo(saved.detectionId());
        assertThat(alert.get("data").get("beachId").longValue()).isEqualTo(beach.getId());
        assertThat(alert.get("data").get("beachName").asString()).isEqualTo(beach.getName());
        assertThat(alert.get("data").get("imageUrl").asString()).isEqualTo("https://images.example.com/person.jpg");
        assertThat(alert.get("data").get("detectedAt").asString()).isEqualTo(DETECTED_AT.toString());
        assertThat(alert.get("data").get("latitude").doubleValue()).isEqualTo(35.1587);
        assertThat(alert.get("data").get("longitude").doubleValue()).isEqualTo(129.1604);
        assertThat(connection.getResponse().getContentAsString()).contains("id:" + saved.detectionId(), "event:person-detected");
        polls.getFirst().run();
        assertThat(payloads(connection)).hasSize(2);
        streams.closeAll();
        mvc.perform(asyncDispatch(connection)).andExpect(status().isOk());
    }

    @Test
    void multipleRegistrationsRequireSelectionAndSendOnlySelectedBeachWithAiResult() throws Exception {
        guard.registerBeach(otherBeach);
        users.saveAndFlush(guard);
        mvc.perform(get(URL).header("Authorization", "Bearer " + token).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isBadRequest());
        var connection = mvc.perform(get(URL).param("beachId", otherBeach.getId().toString())
                        .header("Authorization", "Bearer " + token).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk()).andExpect(request().asyncStarted()).andReturn();
        record(beach.getId(), "unselected.jpg");
        alerts.record(otherShip.getId(), java.util.UUID.randomUUID().toString(), "https://images.example.com/selected.jpg", DETECTED_AT, 35.1, 129.1, false);
        polls.getFirst().run();
        var data = payloads(connection);
        assertThat(data).hasSize(2);
        assertThat(data.get(1).get("data").get("beachId").longValue()).isEqualTo(otherBeach.getId());
        assertThat(data.get(1).get("data").get("aiResult").booleanValue()).isFalse();
    }

    @Test
    void replaysOnlyEventsAfterLastReceivedIdInBatches() throws Exception {
        var first = record(beach.getId(), "first.jpg");
        for (int i = 0; i < 101; i++) record(beach.getId(), "person-" + i + ".jpg");
        var connection = connect(first.detectionId().toString());
        polls.getFirst().run();
        assertThat(payloads(connection)).hasSize(101);
        polls.getFirst().run();
        var data = payloads(connection);
        assertThat(data).hasSize(102);
        var ids = data.subList(1, data.size()).stream().map(node -> node.get("data").get("detectionId").longValue()).toList();
        assertThat(ids).isSorted().doesNotHaveDuplicates().doesNotContain(first.detectionId());
    }

    @Test
    void noDetectionsSendsOnlyConnectedAndHeartbeat() throws Exception {
        var connection = connect(null);
        polls.getFirst().run();
        assertThat(payloads(connection)).hasSize(1);
        when(clock.instant()).thenReturn(Instant.now().plusSeconds(16));
        polls.getFirst().run();
        assertThat(connection.getResponse().getContentAsString()).contains(":heartbeat");
        assertThat(payloads(connection)).hasSize(1);
    }

    @Test
    void rejectsMissingInvalidAndAdminAuthentication() throws Exception {
        mvc.perform(get(URL).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized())
                .andExpect(CommonResponseAssertions::assertCommonResponse)
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(get(URL).header("Authorization", "Bearer broken").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized())
                .andExpect(CommonResponseAssertions::assertCommonResponse);
        ReflectionTestUtils.setField(guard, "role", Role.ADMIN);
        users.saveAndFlush(guard);
        mvc.perform(get(URL).header("Authorization", "Bearer " + token).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isForbidden())
                .andExpect(CommonResponseAssertions::assertCommonResponse)
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        assertThat(polls).isEmpty();
    }

    @Test
    void rejectsUnassignedBeachAndForeignOrInvalidCursor() throws Exception {
        var foreign = record(otherBeach.getId(), "foreign.jpg");
        for (String cursor : new String[]{"-1", "abc", "", "99999999", foreign.detectionId().toString()}) {
            mvc.perform(get(URL).header("Authorization", "Bearer " + token)
                            .header("Last-Event-ID", cursor).accept(MediaType.TEXT_EVENT_STREAM))
                    .andExpect(status().isBadRequest())
                    .andExpect(CommonResponseAssertions::assertCommonResponse)
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
        guard.clearRegisteredBeaches();
        users.saveAndFlush(guard);
        mvc.perform(get(URL).header("Authorization", "Bearer " + token).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isForbidden())
                .andExpect(CommonResponseAssertions::assertCommonResponse)
                .andExpect(jsonPath("$.code").value("BEACH_NOT_ASSIGNED"));
    }

    @Test
    void logoutClosesAnExistingStreamBeforeAnotherNotification() throws Exception {
        var connection = connect(null);
        mvc.perform(post("/auth/logout").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        record(beach.getId(), "after-logout.jpg");
        polls.getFirst().run();
        assertThat(payloads(connection)).hasSize(1);
        verify(tasks.getFirst()).cancel(false);
        mvc.perform(asyncDispatch(connection)).andExpect(status().isOk());
        mvc.perform(get(URL).header("Authorization", "Bearer " + token).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("TOKEN_REVOKED"));
    }

    @Test
    void expirationClosesAnExistingStream() throws Exception {
        var connection = connect(null);
        when(clock.instant()).thenReturn(Instant.now().plusSeconds(3601));
        polls.getFirst().run();
        assertThat(payloads(connection)).hasSize(1);
        verify(tasks.getFirst()).cancel(false);
        mvc.perform(asyncDispatch(connection)).andExpect(status().isOk());
    }

    @Test
    void beachReassignmentClosesStreamWithoutLeakingNewBeachEvents() throws Exception {
        var connection = connect(null);
        guard.clearRegisteredBeaches();
        guard.registerBeach(otherBeach);
        users.saveAndFlush(guard);
        record(otherBeach.getId(), "new-beach.jpg");
        record(beach.getId(), "old-beach.jpg");
        polls.getFirst().run();
        assertThat(payloads(connection)).hasSize(1);
        verify(tasks.getFirst()).cancel(false);
        mvc.perform(asyncDispatch(connection)).andExpect(status().isOk());
    }

    @Test
    void enforcesConnectionLimitAndReleasesSlots() throws Exception {
        connect(null);
        connect(null);
        connect(null);
        mvc.perform(get(URL).header("Authorization", "Bearer " + token).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isTooManyRequests())
                .andExpect(CommonResponseAssertions::assertCommonResponse)
                .andExpect(jsonPath("$.code").value("TOO_MANY_ALERT_CONNECTIONS"));
        streams.closeAll();
        tasks.forEach(task -> verify(task).cancel(false));
        connect(null);
    }

    @Test
    void rejectsUnsupportedResponseTypeBeforeOpeningStream() throws Exception {
        mvc.perform(get(URL).header("Authorization", "Bearer " + token).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotAcceptable())
                .andExpect(CommonResponseAssertions::assertCommonResponse);
        assertThat(polls).isEmpty();
    }

    private MvcResult connect(String lastEventId) throws Exception {
        var request = get(URL).header("Authorization", "Bearer " + token).accept(MediaType.TEXT_EVENT_STREAM);
        if (lastEventId != null) request.header("Last-Event-ID", lastEventId);
        var result = mvc.perform(request).andExpect(status().isOk()).andExpect(request().asyncStarted())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Accel-Buffering", "no")).andReturn();
        var data = payloads(result);
        assertEnvelope(data.getFirst(), "DROWNING_ALERT_STREAM_CONNECTED");
        assertThat(data.getFirst().get("data").isNull()).isTrue();
        return result;
    }

    private com.neoul.ex.domain.alert.dto.DrowningAlertResponse record(Long beachId, String filename) {
        Long shipId = beachId.equals(beach.getId()) ? ownShip.getId() : otherShip.getId();
        return alerts.record(shipId, java.util.UUID.randomUUID().toString(), "https://images.example.com/" + filename, DETECTED_AT, 35.1587, 129.1604);
    }

    private List<JsonNode> payloads(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString().lines().filter(line -> line.startsWith("data:"))
                .map(line -> mapper.readTree(line.substring(5))).toList();
    }

    private void assertEnvelope(JsonNode node, String code) {
        assertThat(node.size()).isEqualTo(5);
        assertThat(node.get("success").booleanValue()).isTrue();
        assertThat(node.get("status").intValue()).isEqualTo(200);
        assertThat(node.get("code").asString()).isEqualTo(code);
        assertThat(node.get("message").isString()).isTrue();
        assertThat(node.has("data")).isTrue();
    }
}
