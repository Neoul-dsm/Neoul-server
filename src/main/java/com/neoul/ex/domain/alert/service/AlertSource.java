package com.neoul.ex.domain.alert.service;

import com.neoul.ex.domain.alert.dto.AlertNotification;
import com.neoul.ex.global.security.AccessTokenPrincipal;
import com.neoul.ex.global.exception.BusinessException;
import com.neoul.ex.global.exception.ErrorCode;

import java.util.List;

public interface AlertSource<T extends AlertNotification> {
    Long authorize(AccessTokenPrincipal principal, Long beachId);
    boolean hasEvent(Long beachId, long eventId);
    List<T> getNext(AccessTokenPrincipal principal, Long beachId, long cursor);

    default long validateCursor(Long beachId, String lastEventId) {
        if (lastEventId == null) return 0;
        try {
            long cursor = Long.parseLong(lastEventId);
            if (cursor < 0 || (cursor > 0 && !hasEvent(beachId, cursor))) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
            return cursor;
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }
}
