package com.neoul.ex.domain.auth.service;

public interface VerificationMailSender {
    void sendCode(String email, String code);
}
