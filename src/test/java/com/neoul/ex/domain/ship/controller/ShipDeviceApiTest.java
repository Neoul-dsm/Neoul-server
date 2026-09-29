package com.neoul.ex.domain.ship.controller;

import com.neoul.ex.domain.beach.entity.Beach;
import com.neoul.ex.domain.beach.repository.BeachRepository;
import com.neoul.ex.domain.ship.entity.Ship;
import com.neoul.ex.domain.ship.repository.ShipRepository;
import com.neoul.ex.domain.user.entity.User;
import com.neoul.ex.domain.user.entity.enums.Role;
import com.neoul.ex.domain.user.repository.UserRepository;
import com.neoul.ex.global.security.JwtProvider;
import com.neoul.ex.global.testutil.CommonResponseAssertions;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ShipDeviceApiTest {
    @Autowired MockMvc mvc;
    @Autowired BeachRepository beaches;
    @Autowired ShipRepository ships;
    @Autowired UserRepository users;
    @Autowired JwtProvider jwt;
    @Autowired JsonMapper mapper;
    @Autowired EntityManager em;
    private Ship ship;
    private Ship otherShip;
    private User admin;
    private String adminToken;
    private String guardToken;

    @BeforeEach
    void setUp() {
        var beach = beaches.saveAndFlush(Beach.create("장치 인증 해변"));
        ship = ships.saveAndFlush(Ship.create("DEVICE-01", "인증 배", beach));
        otherShip = ships.saveAndFlush(Ship.create("DEVICE-02", "다른 배", beach));
        var guard = users.saveAndFlush(User.createGuard("device-guard@example.com", "unused", beach));
        admin = User.createGuard("device-admin@example.com", "unused", null);
        ReflectionTestUtils.setField(admin, "role", Role.ADMIN);
        users.saveAndFlush(admin);
        adminToken = jwt.createAccessToken(admin.getId(), Role.ADMIN);
        guardToken = jwt.createAccessToken(guard.getId(), Role.GUARD);
    }

    @Test
    void adminReceivesAKeyOnceAndOnlyItsHashIsStored() throws Exception {
        String key = issue(ship.getId());
        assertThat(key).matches("[A-Za-z0-9_-]{43}");
        ships.flush();
        em.clear();
        String hash = ships.findById(ship.getId()).orElseThrow().getApiKeyHash();
        assertThat(hash).isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(key.getBytes(StandardCharsets.UTF_8))));
        mvc.perform(get("/ships/{id}", ship.getId()).header("Authorization", "Bearer " + guardToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.apiKey").doesNotExist())
                .andExpect(jsonPath("$.data.apiKeyHash").doesNotExist());
    }

    @Test
    void onlyAnActiveAdminCanIssueOrRevokeKeys() throws Exception {
        for (var request : new org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder[]{
                post("/ships/{id}/api-key", ship.getId()), delete("/ships/{id}/api-key", ship.getId())}) {
            mvc.perform(request).andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/ships/{id}/api-key", ship.getId()).header("Authorization", "Bearer " + guardToken))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/ships/{id}/api-key", ship.getId()).header("Authorization", "Bearer " + guardToken))
                .andExpect(status().isForbidden());
        mvc.perform(post("/auth/logout").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mvc.perform(post("/ships/{id}/api-key", ship.getId()).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isUnauthorized());
        assertThat(ship.getApiKeyHash()).isNull();
    }

    @Test
    void aDeviceUpdatesOnlyItsOwnPositionAndCanSendAHeartbeat() throws Exception {
        String key = issue(ship.getId());
        Instant before = Instant.now().minusSeconds(1);
        send(ship.getId(), key, "{\"latitude\":35.1587,\"longitude\":129.1604}")
                .andExpect(status().isOk()).andExpect(CommonResponseAssertions::assertCommonResponse)
                .andExpect(jsonPath("$.code").value("SHIP_STATUS_RECEIVED"));
        ships.flush();
        em.clear();
        var updated = ships.findById(ship.getId()).orElseThrow();
        assertThat(updated.getLatitude()).isEqualTo(35.1587);
        assertThat(updated.getLongitude()).isEqualTo(129.1604);
        assertThat(updated.getLastCommunicationAt()).isBetween(before, Instant.now());
        Instant locationTime = updated.getLocationReceivedAt();
        send(ship.getId(), key, "{}").andExpect(status().isOk());
        ships.flush();
        em.clear();
        updated = ships.findById(ship.getId()).orElseThrow();
        assertThat(updated.getLatitude()).isEqualTo(35.1587);
        assertThat(updated.getLocationReceivedAt()).isEqualTo(locationTime);
        mvc.perform(get("/ships/{id}/connection", ship.getId()).header("Authorization", "Bearer " + guardToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.connectionStatus").value("ONLINE"));
    }

    @Test
    void missingWrongForeignAndDuplicateKeysAreRejectedWithoutUpdatingTheShip() throws Exception {
        String key = issue(ship.getId());
        issue(otherShip.getId());
        mvc.perform(post("/ships/{id}/status", ship.getId()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_SHIP_API_KEY"));
        send(ship.getId(), "A".repeat(43), "{}").andExpect(status().isUnauthorized());
        send(otherShip.getId(), key, "{}").andExpect(status().isUnauthorized());
        send(Long.MAX_VALUE, key, "{}").andExpect(status().isUnauthorized());
        send(ship.getId(), "invalid", "{}").andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "ApiKey"));
        mvc.perform(post("/ships/{id}/status", ship.getId()).header("X-API-Key", key, key)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        assertThat(ship.getLastCommunicationAt()).isNull();
        assertThat(otherShip.getLastCommunicationAt()).isNull();
    }

    @Test
    void rotationAndRevocationInvalidateOldKeys() throws Exception {
        String old = issue(ship.getId());
        String current = issue(ship.getId());
        assertThat(current).isNotEqualTo(old);
        send(ship.getId(), old, "{}").andExpect(status().isUnauthorized());
        send(ship.getId(), current, "{}").andExpect(status().isOk());
        mvc.perform(delete("/ships/{id}/api-key", ship.getId()).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("SHIP_API_KEY_REVOKED"));
        send(ship.getId(), current, "{}").andExpect(status().isUnauthorized());
        assertThat(ship.getApiKeyHash()).isNull();
    }

    @Test
    void deviceKeysDoNotGrantUserAccessAndUserTokensDoNotAuthenticateDevices() throws Exception {
        String key = issue(ship.getId());
        mvc.perform(get("/ships").header("X-API-Key", key)).andExpect(status().isForbidden());
        mvc.perform(post("/ships/{id}/api-key", ship.getId()).header("X-API-Key", key))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/ships/{id}/status", ship.getId()).header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        assertThat(ship.getLastCommunicationAt()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"latitude\":35}", "{\"longitude\":129}",
            "{\"latitude\":91,\"longitude\":129}", "{\"latitude\":35,\"longitude\":181}",
            "{\"latitude\":-91,\"longitude\":129}", "{\"latitude\":35,\"longitude\":-181}",
            "{\"latitude\":1e309,\"longitude\":129}"})
    void invalidCoordinatesDoNotUpdatePositionOrHeartbeat(String body) throws Exception {
        String key = issue(ship.getId());
        send(ship.getId(), key, body).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        assertThat(ship.getLastCommunicationAt()).isNull();
        assertThat(ship.getLatitude()).isNull();
    }

    private String issue(Long shipId) throws Exception {
        var result = mvc.perform(post("/ships/{id}/api-key", shipId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.shipId").value(shipId)).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).get("data").get("apiKey").asString();
    }

    private ResultActions send(Long shipId, String key, String body) throws Exception {
        return mvc.perform(post("/ships/{id}/status", shipId).header("X-API-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
