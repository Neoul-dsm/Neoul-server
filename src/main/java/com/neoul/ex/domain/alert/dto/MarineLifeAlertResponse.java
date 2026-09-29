package com.neoul.ex.domain.alert.dto;

import com.neoul.ex.domain.alert.entity.MarineLifeLog;

import java.time.Instant;

public record MarineLifeAlertResponse
        (Long detectionId,
         String species,
         Long beachId,
         String beachName,
         Instant detectedAt
        ) implements AlertNotification {
    public static MarineLifeAlertResponse from(MarineLifeLog log) {
        return new MarineLifeAlertResponse(log.getId(),
                log.getMarineType(),
                log.getShip()
                        .getBeach()
                        .getId(),
                log.getShip()
                        .getBeach()
                        .getName(),
                log.getDetectedAt());
    }
}
