package com.neoul.ex.auth.service;

import com.neoul.ex.auth.dto.SignupRequest;
import com.neoul.ex.auth.dto.SignupResponse;
import com.neoul.ex.beach.entity.Beach;
import com.neoul.ex.beach.repository.BeachRepository;
import com.neoul.ex.global.exception.BusinessException;
import com.neoul.ex.user.entity.User;
import com.neoul.ex.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final BeachRepository beachRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (!request.password().equals(request.passwordConfirm())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "비밀번호가 일치하지 않습니다.");
        }

        if (userRepository.existsByLoginId(request.loginId())) {
            throw new BusinessException(HttpStatus.CONFLICT, "이미 사용 중인 아이디입니다.");
        }

        Beach beach = beachRepository.findById(request.beachId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "존재하지 않는 해수욕장입니다."));

        String encodedPassword = passwordEncoder.encode(request.password());
        User user = User.createGuard(request.loginId(), encodedPassword, beach);
        User savedUser = userRepository.save(user);

        return SignupResponse.from(savedUser);
    }
}
