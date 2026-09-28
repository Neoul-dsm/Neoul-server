package com.neoul.ex.domain.alert.service;

import com.neoul.ex.domain.alert.dto.MarineLifeAlertResponse;
import com.neoul.ex.domain.alert.entity.MarineLog;
import com.neoul.ex.domain.alert.repository.MarineLogRepository;
import com.neoul.ex.domain.user.entity.enums.Role;
import com.neoul.ex.global.security.AccessTokenPrincipal;
import com.neoul.ex.domain.auth.service.ResourceAccessService;
import com.neoul.ex.domain.beach.repository.BeachRepository;
import com.neoul.ex.global.exception.BusinessException;
import com.neoul.ex.global.exception.ErrorCode;
import com.neoul.ex.domain.ship.repository.ShipRepository;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MarineLifeAlertService implements AlertSource<MarineLifeAlertResponse> {
    private static final int BATCH_SIZE = 100;
    private final ResourceAccessService access;
    private final BeachRepository beaches;
    private final ShipRepository ships;
    private final MarineLogRepository logs;

    @Override
    public Long authorize(AccessTokenPrincipal principal) {
        return access.requireAlertBeach(principal, Set.of(Role.ADMIN, Role.GUARD));
    }

    @Override
    public long validateCursor(Long beachId, String lastEventId) {
        if (lastEventId == null) return 0;
        try {
            long cursor = Long.parseLong(lastEventId);
            if (cursor < 0 || (cursor > 0 && !logs.existsByIdAndShipBeachId(cursor, beachId))) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
            return cursor;
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    @Override
    public List<MarineLifeAlertResponse> getNext(AccessTokenPrincipal principal, Long beachId, long cursor) {
        if (!authorize(principal).equals(beachId)) throw new BusinessException(ErrorCode.FORBIDDEN);
        return logs.findByShipBeachIdAndIdGreaterThanOrderByIdAsc(beachId, cursor, PageRequest.of(0, BATCH_SIZE))
                .stream().map(MarineLifeAlertResponse::from).toList();
    }

    // Future ingestion adapters use this method so IDs follow commit order within a beach.
    @Transactional
    public MarineLifeAlertResponse record(Long shipId, String species, int count, Instant detectedAt) {
        if (shipId == null || shipId <= 0) throw new BusinessException(ErrorCode.INVALID_INPUT);
        var ship = ships.findById(shipId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHIP_NOT_FOUND));
        beaches.findByIdForUpdate(ship.getBeach().getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.BEACH_NOT_FOUND));
        MarineLog log;
        try {
            log = MarineLog.create(ship, species, count, detectedAt);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return MarineLifeAlertResponse.from(logs.saveAndFlush(log));
    }
}
