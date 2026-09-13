package com.neoul.ex.auth.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SignupRequestValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        validatorFactory.close();
    }

    @Test
    void acceptsValidSignupRequest() {
        assertThat(validator.validate(request("guard123", "Abcd1234!"))).isEmpty();
    }

    @Test
    void rejectsLoginIdWithOnlyLetters() {
        assertThat(validator.validate(request("abcdef", "Abcd1234!"))).isNotEmpty();
    }

    @Test
    void rejectsLoginIdWithOnlyNumbers() {
        assertThat(validator.validate(request("123456", "Abcd1234!"))).isNotEmpty();
    }

    @Test
    void rejectsLoginIdLongerThanTenCharacters() {
        assertThat(validator.validate(request("guard123456", "Abcd1234!"))).isNotEmpty();
    }

    @Test
    void rejectsLoginIdWithSpecialCharacter() {
        assertThat(validator.validate(request("guard_123", "Abcd1234!"))).isNotEmpty();
    }

    @Test
    void rejectsPasswordShorterThanEightCharacters() {
        assertThat(validator.validate(request("guard123", "Abc1!"))).isNotEmpty();
    }

    @Test
    void rejectsPasswordWithoutSpecialCharacter() {
        assertThat(validator.validate(request("guard123", "Abcd1234"))).isNotEmpty();
    }

    private SignupRequest request(String loginId, String password) {
        return new SignupRequest(loginId, password, password, 1L);
    }
}
