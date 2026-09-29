package com.neoul.ex.domain.ship.service;

import com.neoul.ex.domain.auth.service.ResourceAccessService;
import com.neoul.ex.domain.ship.entity.Ship;
import com.neoul.ex.domain.ship.repository.ShipRepository;
import com.neoul.ex.global.exception.BusinessException;
import com.neoul.ex.global.exception.ErrorCode;
import com.neoul.ex.global.security.AccessTokenPrincipal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ShipDeviceService {
    private final ShipRepository ships;
    private final ResourceAccessService access;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public String issueApiKey(Long shipId, AccessTokenPrincipal principal) {
        requireAdmin(principal);
        Ship ship = findShipForUpdate(shipId);
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String apiKey = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        ship.changeApiKeyHash(hash(apiKey));
        return apiKey;
    }

    public void revokeApiKey(Long shipId, AccessTokenPrincipal principal) {
        requireAdmin(principal);
        findShipForUpdate(shipId).changeApiKeyHash(null);
    }

    public void receiveStatus(Long shipId, String apiKey, Double latitude, Double longitude) {
        if (apiKey == null || !apiKey.matches("[A-Za-z0-9_-]{43}")) {
            throw new BusinessException(ErrorCode.INVALID_SHIP_API_KEY);
        }
        validateId(shipId);
        // Serialize key rotation/revocation and status writes for this ship.
        Ship ship = ships.findByIdForUpdate(shipId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_SHIP_API_KEY));
        String expected = ship.getApiKeyHash();
        if (expected == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                hash(apiKey).getBytes(StandardCharsets.US_ASCII))) {
            throw new BusinessException(ErrorCode.INVALID_SHIP_API_KEY);
        }
        if ((latitude == null) != (longitude == null)
                || (latitude != null && (!Double.isFinite(latitude) || latitude < -90 || latitude > 90
                || !Double.isFinite(longitude) || longitude < -180 || longitude > 180))) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        var receivedAt = clock.instant();
        ship.updateLastCommunicationAt(receivedAt);
        if (latitude != null) ship.updateLocation(latitude, longitude, receivedAt);
    }

    private void requireAdmin(AccessTokenPrincipal principal) {
        if (!access.readScope(principal).isAdmin()) throw new BusinessException(ErrorCode.FORBIDDEN);
    }

    private Ship findShipForUpdate(Long shipId) {
        validateId(shipId);
        return ships.findByIdForUpdate(shipId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHIP_NOT_FOUND));
    }

    private void validateId(Long shipId) {
        if (shipId == null || shipId <= 0) throw new BusinessException(ErrorCode.INVALID_INPUT);
    }

    private String hash(String apiKey) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(apiKey.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
