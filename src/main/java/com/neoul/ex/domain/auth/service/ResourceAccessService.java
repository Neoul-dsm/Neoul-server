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
        if (user.getRole() == Role.GUARD && user.getBeach() == null) {
            throw new BusinessException(ErrorCode.BEACH_NOT_ASSIGNED);
        }
        return new Scope(user.getRole(), user.getBeach() == null ? null : user.getBeach().getId());
    }

    @Transactional(readOnly = true)
    public Long requireAlertBeach(AccessTokenPrincipal principal, Set<Role> allowedRoles) {
        var user = sessions.requireActiveUser(principal);
        if (!allowedRoles.contains(user.getRole())) throw new BusinessException(ErrorCode.FORBIDDEN);
        if (user.getBeach() == null) throw new BusinessException(ErrorCode.BEACH_NOT_ASSIGNED);
        return user.getBeach().getId();
    }

    public record Scope(Role role, Long beachId) {
        public boolean isAdmin() { return role == Role.ADMIN; }

        public void requireBeach(Long requestedBeachId) {
            if (!isAdmin() && !beachId.equals(requestedBeachId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
        }
    }
}
