package com.neoul.ex.domain.auth.service;

import com.neoul.ex.domain.auth.dto.EmailVerificationConfirmedResponse;
import com.neoul.ex.domain.auth.dto.EmailVerificationSentResponse;
import com.neoul.ex.domain.auth.entity.EmailVerification;
import com.neoul.ex.domain.auth.repository.EmailVerificationRepository;
import com.neoul.ex.domain.user.repository.UserRepository;
import com.neoul.ex.global.exception.BusinessException;
import com.neoul.ex.global.exception.ErrorCode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailVerificationService {
    private static final int MAX_ATTEMPTS = 5;
    private final EmailVerificationRepository verifications;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final VerificationMailSender mail;
    private final TransactionTemplate creation;
    private final TransactionTemplate issuance;

    public EmailVerificationService(EmailVerificationRepository verifications, UserRepository users,
            PasswordEncoder encoder, Clock clock, VerificationMailSender mail, PlatformTransactionManager manager) {
        this.verifications = verifications;
        this.users = users;
        this.encoder = encoder;
        this.clock = clock;
        this.mail = mail;
        this.creation = new TransactionTemplate(manager);
        this.creation.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.issuance = new TransactionTemplate(manager);
    }

    public EmailVerificationSentResponse send(String email) {
        if (users.existsByEmail(email)) throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        ensureExists(email);
        return issuance.execute(status -> issueCode(email));
    }

    private void ensureExists(String email) {
        if (verifications.existsById(email)) return;
        try {
            // Isolate a competing first insert so its failure cannot roll back code issuance.
            creation.executeWithoutResult(status -> verifications.saveAndFlush(EmailVerification.pending(email)));
        } catch (DataIntegrityViolationException exception) {
            if (!verifications.existsById(email)) throw exception;
        }
    }

    private EmailVerificationSentResponse issueCode(String email) {
        var verification = verifications.findByEmailForUpdate(email).orElseThrow();
        if (users.existsByEmail(email)) throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        var now = clock.instant();
        if (verification.getResendAvailableAt() != null && now.isBefore(verification.getResendAvailableAt())) {
            throw new BusinessException(ErrorCode.EMAIL_RESEND_TOO_SOON);
        }
        String code;
        do {
            code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        } while (verification.getCodeHash() != null && encoder.matches(code, verification.getCodeHash()));
        String codeHash = encoder.encode(code);
        mail.sendCode(email, code);
        verification.issue(codeHash, clock.instant());
        return new EmailVerificationSentResponse(verification.getExpiresAt(), verification.getResendAvailableAt());
    }

    // Failed guesses must commit their attempt count even though the API returns an error.
    @Transactional(noRollbackFor = BusinessException.class)
    public EmailVerificationConfirmedResponse confirm(String email, String code) {
        var verification = verifications.findByEmailForUpdate(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.EMAIL_CODE_INVALID));
        if (users.existsByEmail(email)) throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        if (verification.getCodeHash() == null || verification.getProofHash() != null) {
            throw new BusinessException(ErrorCode.EMAIL_CODE_INVALID);
        }
        if (!clock.instant().isBefore(verification.getExpiresAt())) {
            throw new BusinessException(ErrorCode.EMAIL_CODE_EXPIRED);
        }
        if (verification.getFailedAttempts() >= MAX_ATTEMPTS) {
            throw new BusinessException(ErrorCode.EMAIL_CODE_ATTEMPTS_EXCEEDED);
        }
        if (code == null || !code.matches("[0-9]{6}") || !encoder.matches(code, verification.getCodeHash())) {
            verification.failAttempt();
            throw new BusinessException(verification.getFailedAttempts() >= MAX_ATTEMPTS
                    ? ErrorCode.EMAIL_CODE_ATTEMPTS_EXCEEDED : ErrorCode.EMAIL_CODE_INVALID);
        }
        // BCrypt comparison takes time so recheck the expiry immediately before issuing proof.
        var now = clock.instant();
        if (!now.isBefore(verification.getExpiresAt())) throw new BusinessException(ErrorCode.EMAIL_CODE_EXPIRED);
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        verification.verify(hash(token), now.plusSeconds(600));
        return new EmailVerificationConfirmedResponse(token, verification.getProofExpiresAt());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void consume(String email, String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
        }
        var verification = verifications.findByEmailForUpdate(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED));
        if (verification.getProofHash() == null || verification.getProofExpiresAt() == null
                || !clock.instant().isBefore(verification.getProofExpiresAt())
                || !MessageDigest.isEqual(hash(token).getBytes(StandardCharsets.US_ASCII),
                        verification.getProofHash().getBytes(StandardCharsets.US_ASCII))) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
        }
        verification.consume();
    }

    private String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
