package com.neoul.ex.domain.alert.service;

import com.neoul.ex.domain.alert.dto.AlertNotification;
import com.neoul.ex.global.security.AccessTokenPrincipal;

import java.util.List;

public interface AlertSource<T extends AlertNotification> {
    Long authorize(AccessTokenPrincipal principal);
    long validateCursor(Long beachId, String lastEventId);
    List<T> getNext(AccessTokenPrincipal principal, Long beachId, long cursor);
}
