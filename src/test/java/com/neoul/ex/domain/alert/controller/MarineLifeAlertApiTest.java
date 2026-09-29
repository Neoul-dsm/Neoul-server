package com.neoul.ex.domain.alert.controller;

import com.neoul.ex.domain.alert.dto.DrowningAlertResponse;
import com.neoul.ex.domain.alert.dto.MarineLifeAlertResponse;
import com.neoul.ex.domain.alert.repository.MarineLifeLogRepository;
import com.neoul.ex.domain.alert.repository.HumanLogRepository;
import com.neoul.ex.domain.alert.service.DrowningAlertService;
import com.neoul.ex.domain.alert.service.MarineLifeAlertService;
import com.neoul.ex.domain.alert.service.PollingAlertStream;
import com.neoul.ex.domain.user.entity.User;
import com.neoul.ex.domain.user.entity.enums.Role;
import com.neoul.ex.domain.auth.repository.RevokedAccessTokenRepository;
import com.neoul.ex.domain.user.repository.UserRepository;
import com.neoul.ex.global.security.JwtProvider;
import com.neoul.ex.domain.beach.entity.Beach;
import com.neoul.ex.domain.beach.repository.BeachRepository;
import com.neoul.ex.global.exception.BusinessException;
import com.neoul.ex.global.testutil.CommonResponseAssertions;
import com.neoul.ex.domain.ship.entity.Ship;
import com.neoul.ex.domain.ship.repository.ShipRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
class MarineLifeAlertApiTest {
    private static final String URL = "/alerts/marineanimal/stream";
    private static final Instant DETECTED_AT = Instant.parse("2026-09-20T01:23:45Z");
    @Autowired private MockMvc mvc;
    @Autowired private MarineLifeAlertService alerts;
    @Autowired private PollingAlertStream<com.neoul.ex.domain.alert.dto.MarineLifeAlertResponse> streams;
    @Autowired private DrowningAlertService drowningAlerts;
    @Autowired private PollingAlertStream<com.neoul.ex.domain.alert.dto.DrowningAlertResponse> drowningStreams;
    @Autowired private MarineLifeLogRepository detections;
    @Autowired private HumanLogRepository personDetections;
    @Autowired private BeachRepository beaches;
    @Autowired private ShipRepository ships;
    @Autowired private UserRepository users;
    @Autowired private RevokedAccessTokenRepository revokedTokens;
    @Autowired private JwtProvider jwt;
    @Autowired private JsonMapper mapper;
    @MockitoBean(name = "marineLifeAlertScheduler") private TaskScheduler scheduler;
    @MockitoBean(name = "drowningAlertScheduler") private TaskScheduler drowningScheduler;
    @MockitoBean private Clock clock;
    private final List<Runnable> polls = new ArrayList<>();
    private final List<Runnable> drowningPolls = new ArrayList<>();
    private Beach beach;
    private Beach otherBeach;
    private Ship ownShip;
    private Ship otherShip;
    private User user;
    private String token;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(Instant.now());
        polls.clear();
        drowningPolls.clear();
        capturePolls(scheduler, polls);
        capturePolls(drowningScheduler, drowningPolls);
        beach = beaches.saveAndFlush(Beach.create("유해 생물 검증 해변"));
        otherBeach = beaches.saveAndFlush(Beach.create("다른 해변"));
        ownShip = ships.saveAndFlush(Ship.create("OWN-01", "담당 배", beach));
        otherShip = ships.saveAndFlush(Ship.create("OTHER-01", "다른 배", otherBeach));
        user = users.saveAndFlush(User.createGuard("marine@example.com", "unused-password", beach));
        token = jwt.createAccessToken(user.getId(), Role.GUARD);
    }

    @AfterEach
    void cleanUp() {
        streams.closeAll();
        drowningStreams.closeAll();
        detections.deleteAll();
        personDetections.deleteAll();
        revokedTokens.deleteAll();
        users.deleteById(user.getId());
        ships.deleteById(ownShip.getId());
        ships.deleteById(otherShip.getId());
        beaches.deleteById(beach.getId());
        beaches.deleteById(otherBeach.getId());
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void bothRolesReceiveSpeciesBeachAndTimeOnlyForTheirAssignedBeach(Role role) throws Exception {
        ReflectionTestUtils.setField(user, "role", role);
        users.saveAndFlush(user);
        alerts.record(otherShip.getId(), java.util.UUID.randomUUID().toString(), "상어", 1, DETECTED_AT);
        var connection = connect(null);
        var saved = alerts.record(ownShip.getId(), java.util.UUID.randomUUID().toString(), " 해파리 ", 1, DETECTED_AT);
        polls.getFirst().run();
        var events = payloads(connection);
        assertThat(events).hasSize(2);
        var event = events.get(1);
        assertThat(event.size()).isEqualTo(5);
        assertThat(event.get("success").booleanValue()).isTrue();
        assertThat(event.get("status").intValue()).isEqualTo(200);
        assertThat(event.get("code").asString()).isEqualTo("MARINE_LIFE_DETECTED");
        assertThat(event.get("message").asString()).isEqualTo("유해 생물이 감지되었습니다.");
        var data = event.get("data");
        assertThat(data.size()).isEqualTo(5);
        assertThat(data.get("detectionId").longValue()).isEqualTo(saved.detectionId());
        assertThat(data.get("species").asString()).isEqualTo("해파리");
        assertThat(data.get("beachId").longValue()).isEqualTo(beach.getId());
        assertThat(data.get("beachName").asString()).isEqualTo("유해 생물 검증 해변");
        assertThat(data.get("detectedAt").asString()).isEqualTo(DETECTED_AT.toString());
        polls.getFirst().run();
        assertThat(payloads(connection)).hasSize(2);
        streams.closeAll();
        mvc.perform(asyncDispatch(connection)).andExpect(status().isOk());
    }

    @Test
    void retriedDeviceEventDoesNotSendAnotherAlert() throws Exception {
        var connection = connect(null);
        String eventId = java.util.UUID.randomUUID().toString();
        var first = alerts.record(ownShip.getId(), eventId, "해파리", 3, DETECTED_AT);
        polls.getFirst().run();
        var retry = alerts.record(ownShip.getId(), eventId, "해파리", 3, DETECTED_AT);
        polls.getFirst().run();
        assertThat(retry.detectionId()).isEqualTo(first.detectionId());
        assertThat(payloads(connection)).hasSize(2);
        assertThat(detections.count()).isEqualTo(1);
        streams.closeAll();
        mvc.perform(asyncDispatch(connection)).andExpect(status().isOk());
    }

    @Test
    void replaysAfterCursorAndRejectsForeignAndInvalidCursors() throws Exception {
        var first = alerts.record(ownShip.getId(), java.util.UUID.randomUUID().toString(), "상어", 1, DETECTED_AT);
        var second = alerts.record(ownShip.getId(), java.util.UUID.randomUUID().toString(), "해파리", 1, DETECTED_AT);
        var foreign = alerts.record(otherShip.getId(), java.util.UUID.randomUUID().toString(), "상어", 1, DETECTED_AT);
        var connection = connect(first.detectionId().toString());
        polls.getFirst().run();
        assertThat(payloads(connection)).hasSize(2);
        assertThat(payloads(connection).get(1).get("data").get("detectionId").longValue()).isEqualTo(second.detectionId());
        for (String cursor : new String[]{"-1", "abc", "", "999999", foreign.detectionId().toString()}) {
            mvc.perform(get(URL).header("Authorization", "Bearer " + token).header("Last-Event-ID", cursor)
                            .accept(MediaType.TEXT_EVENT_STREAM))
                    .andExpect(status().isBadRequest()).andExpect(CommonResponseAssertions::assertCommonResponse)
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
    }

    @Test
    void requiresAuthenticationAndAssignedBeach() throws Exception {
        mvc.perform(get(URL).accept(MediaType.TEXT_EVENT_STREAM)).andExpect(status().isUnauthorized())
                .andExpect(CommonResponseAssertions::assertCommonResponse).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(get(URL).header("Authorization", "Bearer broken").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized()).andExpect(CommonResponseAssertions::assertCommonResponse);
        user.clearRegisteredBeaches();
        users.saveAndFlush(user);
        mvc.perform(get(URL).header("Authorization", "Bearer " + token).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isForbidden()).andExpect(CommonResponseAssertions::assertCommonResponse)
                .andExpect(jsonPath("$.code").value("BEACH_NOT_ASSIGNED"));
    }

    @Test
    void logoutStopsFurtherEventsAndReconnection() throws Exception {
        var connection = connect(null);
        mvc.perform(post("/auth/logout").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        alerts.record(ownShip.getId(), java.util.UUID.randomUUID().toString(), "상어", 1, DETECTED_AT);
        polls.getFirst().run();
        assertThat(payloads(connection)).hasSize(1);
        mvc.perform(asyncDispatch(connection)).andExpect(status().isOk());
        mvc.perform(get(URL).header("Authorization", "Bearer " + token).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("TOKEN_REVOKED"));
    }

    @Test
    void doesNotMixDrowningAndMarineLifeEvents() throws Exception {
        var marine = connect(null);
        var drowning = mvc.perform(get("/alerts/drowning/stream").header("Authorization", "Bearer " + token)
                        .accept(MediaType.TEXT_EVENT_STREAM)).andExpect(request().asyncStarted()).andReturn();
        alerts.record(ownShip.getId(), java.util.UUID.randomUUID().toString(), "상어", 1, DETECTED_AT);
        drowningAlerts.record(ownShip.getId(), java.util.UUID.randomUUID().toString(), "https://images.example.com/person.jpg", DETECTED_AT, 35.1, 129.1);
        polls.getFirst().run();
        drowningPolls.getFirst().run();
        assertThat(payloads(marine)).hasSize(2);
        assertThat(payloads(drowning)).hasSize(2);
        assertThat(payloads(marine).get(1).get("code").asString()).isEqualTo("MARINE_LIFE_DETECTED");
        assertThat(payloads(drowning).get(1).get("code").asString()).isEqualTo("DROWNING_DETECTED");
    }

    @Test
    void beachReassignmentClosesStreamBeforeEitherBeachsEventsCanBeDelivered() throws Exception {
        var connection = connect(null);
        user.clearRegisteredBeaches();
        user.registerBeach(otherBeach);
        users.saveAndFlush(user);
        alerts.record(ownShip.getId(), java.util.UUID.randomUUID().toString(), "해파리", 1, DETECTED_AT);
        alerts.record(otherShip.getId(), java.util.UUID.randomUUID().toString(), "상어", 1, DETECTED_AT);
        polls.getFirst().run();
        assertThat(payloads(connection)).hasSize(1);
        mvc.perform(asyncDispatch(connection)).andExpect(status().isOk());
        var reconnected = connect(null);
        polls.getLast().run();
        assertThat(payloads(reconnected)).hasSize(2);
        assertThat(payloads(reconnected).get(1).get("data").get("beachId").longValue()).isEqualTo(otherBeach.getId());
    }

    @Test
    void rejectsIncompleteDetectionBeforeItCanBeDelivered() {
        for (String species : new String[]{null, "", "  ", "가".repeat(101)}) {
            assertThatThrownBy(() -> alerts.record(ownShip.getId(), java.util.UUID.randomUUID().toString(), species, 1, DETECTED_AT))
                    .isInstanceOf(BusinessException.class).hasMessage("입력값이 올바르지 않습니다.");
        }
        assertThatThrownBy(() -> alerts.record(ownShip.getId(), java.util.UUID.randomUUID().toString(), "상어", 1, null))
                .isInstanceOf(BusinessException.class).hasMessage("입력값이 올바르지 않습니다.");
        assertThat(detections.count()).isZero();
    }

    private void capturePolls(TaskScheduler target, List<Runnable> callbacks) {
        when(target.scheduleWithFixedDelay(any(Runnable.class), any(Instant.class), any(Duration.class)))
                .thenAnswer(invocation -> {
                    callbacks.add(invocation.getArgument(0));
                    return mock(ScheduledFuture.class);
                });
    }

    private MvcResult connect(String cursor) throws Exception {
        var builder = get(URL).header("Authorization", "Bearer " + token).accept(MediaType.TEXT_EVENT_STREAM);
        if (cursor != null) builder.header("Last-Event-ID", cursor);
        var result = mvc.perform(builder).andExpect(status().isOk()).andExpect(request().asyncStarted())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Accel-Buffering", "no")).andReturn();
        assertThat(payloads(result).getFirst().get("code").asString()).isEqualTo("MARINE_LIFE_ALERT_STREAM_CONNECTED");
        return result;
    }

    private List<JsonNode> payloads(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString().lines().filter(line -> line.startsWith("data:"))
                .map(line -> mapper.readTree(line.substring(5))).toList();
    }
}
