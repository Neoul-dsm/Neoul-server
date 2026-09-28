package com.neoul.ex.domain.auth.service;

import com.neoul.ex.global.exception.BusinessException;
import com.neoul.ex.global.exception.ErrorCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class SmtpVerificationMailSenderTest {
    @Test
    void sendsOnlyToRequestedMailboxWithConfiguredSenderAndCode() {
        var mail = mock(JavaMailSender.class);
        var beans = new StaticListableBeanFactory();
        beans.addBean("mail", mail);
        var sender = new SmtpVerificationMailSender(beans.getBeanProvider(JavaMailSender.class),
                "smtp.example.com", "no-reply@example.com");
        sender.sendCode("guard@example.com", "000123");
        var message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(message.capture());
        assertThat(message.getValue().getFrom()).isEqualTo("no-reply@example.com");
        assertThat(message.getValue().getTo()).containsExactly("guard@example.com");
        assertThat(message.getValue().getText()).contains("000123", "3분");
    }

    @Test
    void missingConfigurationFailsWithoutPretendingToSend() {
        var beans = new StaticListableBeanFactory();
        var sender = new SmtpVerificationMailSender(beans.getBeanProvider(JavaMailSender.class), "", "");
        assertThatThrownBy(() -> sender.sendCode("guard@example.com", "000123"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.EMAIL_DELIVERY_FAILED));
    }

    @Test
    void hidesMailTransportErrorDetails() {
        var mail = mock(JavaMailSender.class);
        var beans = new StaticListableBeanFactory();
        beans.addBean("mail", mail);
        doThrow(new MailSendException("private transport detail")).when(mail).send(any(SimpleMailMessage.class));
        var sender = new SmtpVerificationMailSender(beans.getBeanProvider(JavaMailSender.class),
                "smtp.example.com", "no-reply@example.com");
        assertThatThrownBy(() -> sender.sendCode("guard@example.com", "000123"))
                .isInstanceOf(BusinessException.class).hasMessage("인증 이메일을 발송하지 못했습니다.");
    }
}
