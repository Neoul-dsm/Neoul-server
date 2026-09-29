package com.neoul.ex.domain.auth.service;

import com.neoul.ex.domain.user.entity.enums.Role;
import com.neoul.ex.global.security.AccessTokenPrincipal;
import com.neoul.ex.global.exception.BusinessException;
import com.neoul.ex.global.exception.ErrorCode;

import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ResourceAccessService {
    private final TokenSessionService sessions;

    @Transactional(readOnly = true)
    public Scope readScope(AccessTokenPrincipal principal) {
        var user = sessions.requireActiveUser(principal);
        if (user.getRole() != Role.ADMIN && user.getRole() != Role.GUARD) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        var beachIds = user.getRegisteredBeachIds();
        if (user.getRole() == Role.GUARD && beachIds.isEmpty()) {
            throw new BusinessException(ErrorCode.BEACH_NOT_ASSIGNED);
        }
        return new Scope(user.getRole(), beachIds);
    }

    @Transactional(readOnly = true)
    public Long requireAlertBeach(AccessTokenPrincipal principal, Set<Role> allowedRoles, Long requestedBeachId) {
        var user = sessions.requireActiveUser(principal);
        if (!allowedRoles.contains(user.getRole())) throw new BusinessException(ErrorCode.FORBIDDEN);
        var beachIds = user.getRegisteredBeachIds();
        if (beachIds.isEmpty()) throw new BusinessException(ErrorCode.BEACH_NOT_ASSIGNED);
        if (requestedBeachId != null) {
            if (requestedBeachId <= 0) throw new BusinessException(ErrorCode.INVALID_INPUT);
            if (!beachIds.contains(requestedBeachId)) throw new BusinessException(ErrorCode.FORBIDDEN);
            return requestedBeachId;
        }
        if (beachIds.size() != 1) throw new BusinessException(ErrorCode.INVALID_INPUT);
        return beachIds.iterator().next();
    }

    public record Scope(Role role, Set<Long> beachIds) {
        public Scope { beachIds = Set.copyOf(beachIds); }
        public boolean isAdmin() { return role == Role.ADMIN; }

        public void requireBeach(Long requestedBeachId) {
            if (!isAdmin() && !beachIds.contains(requestedBeachId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
        }
    }
}
