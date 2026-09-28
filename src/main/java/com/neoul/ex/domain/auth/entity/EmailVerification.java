package com.neoul.ex.domain.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import org.springframework.data.domain.Persistable;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "email_verifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailVerification implements Persistable<String> {
    @Transient
    private boolean newRecord = true;
    @Id
    @Column(length = 30)
    private String email;
    @Column(length = 100)
    private String codeHash;
    private Instant expiresAt;
    private Instant resendAvailableAt;
    @Column(length = 64)
    private String proofHash;
    private Instant proofExpiresAt;
    private int failedAttempts;

    @Override
    public String getId() { return email; }

    @Override
    public boolean isNew() { return newRecord; }

    @PostLoad
    @PostPersist
    private void markPersisted() { newRecord = false; }

    public static EmailVerification pending(String email) {
        var verification = new EmailVerification();
        verification.email = email;
        return verification;
    }

    public void issue(String codeHash, Instant sentAt) {
        this.codeHash = codeHash;
        this.expiresAt = sentAt.plusSeconds(180);
        this.resendAvailableAt = sentAt.plusSeconds(30);
        this.proofHash = null;
        this.proofExpiresAt = null;
        this.failedAttempts = 0;
    }

    public void failAttempt() { failedAttempts++; }

    public void verify(String proofHash, Instant proofExpiresAt) {
        this.proofHash = proofHash;
        this.proofExpiresAt = proofExpiresAt;
    }

    public void consume() {
        this.proofHash = null;
        this.proofExpiresAt = null;
        this.codeHash = null;
    }
}
