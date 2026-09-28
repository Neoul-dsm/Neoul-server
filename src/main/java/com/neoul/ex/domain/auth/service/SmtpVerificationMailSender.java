package com.neoul.ex.domain.auth.service;

import com.neoul.ex.global.exception.BusinessException;
import com.neoul.ex.global.exception.ErrorCode;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class SmtpVerificationMailSender implements VerificationMailSender {
    private final ObjectProvider<JavaMailSender> senders;
    private final String host;
    private final String from;

    public SmtpVerificationMailSender(ObjectProvider<JavaMailSender> senders,
            @Value("${spring.mail.host:}") String host, @Value("${app.mail.from:}") String from) {
        this.senders = senders;
        this.host = host;
        this.from = from;
    }

    @Override
    public void sendCode(String email, String code) {
        JavaMailSender sender = senders.getIfAvailable();
        if (sender == null || host.isBlank() || from.isBlank()) {
            throw new BusinessException(ErrorCode.EMAIL_DELIVERY_FAILED);
        }
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("너울 회원가입 이메일 인증");
        message.setText("인증 코드는 " + code + " 입니다\n3분 이내에 입력해 주세요");
        try {
            sender.send(message);
        } catch (MailException exception) {
            // Do not include transport errors that may contain addresses or message contents.
            throw new BusinessException(ErrorCode.EMAIL_DELIVERY_FAILED);
        }
    }
}
