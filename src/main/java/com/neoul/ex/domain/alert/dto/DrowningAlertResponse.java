package com.neoul.ex.domain.alert.dto;

import com.neoul.ex.domain.alert.entity.PersonLog;

import java.time.Instant;

public record DrowningAlertResponse(Long detectionId, Long beachId, String beachName, String imageUrl,
                                     Instant detectedAt, double latitude, double longitude) implements AlertNotification {
    public static DrowningAlertResponse from(PersonLog log) {
        return new DrowningAlertResponse(log.getId(), log.getShip().getBeach().getId(),
                log.getShip().getBeach().getName(), log.getImageUrl(), log.getDetectedAt(),
                log.getLatitude(), log.getLongitude());
    }
}
