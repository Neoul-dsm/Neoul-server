package com.neoul.ex.dto;

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
        assertThat(validator.validate(request("guard@example.com", "Abcd1234!"))).isEmpty();
    }

    @Test
    void normalizesEmailBeforeValidation() {
        SignupRequest request = request(" Guard@Example.com ", "Abcd1234!");

        assertThat(validator.validate(request)).isEmpty();
        assertThat(request.email()).isEqualTo("guard@example.com");
    }

    @Test
    void rejectsMalformedEmail() {
        assertThat(validator.validate(request("not-an-email", "Abcd1234!"))).isNotEmpty();
    }

    @Test
    void rejectsBlankEmail() {
        assertThat(validator.validate(request("   ", "Abcd1234!"))).isNotEmpty();
    }

    @Test
    void rejectsEmailLongerThanTwoHundredFiftyFourCharacters() {
        String email = "a".repeat(243) + "@example.com";

        assertThat(validator.validate(request(email, "Abcd1234!"))).isNotEmpty();
    }

    @Test
    void rejectsPasswordShorterThanEightCharacters() {
        assertThat(validator.validate(request("guard@example.com", "Abc1!"))).isNotEmpty();
    }

    @Test
    void rejectsPasswordWithoutSpecialCharacter() {
        assertThat(validator.validate(request("guard@example.com", "Abcd1234"))).isNotEmpty();
    }

    private SignupRequest request(String email, String password) {
        return new SignupRequest(email, password, password, 1L);
    }
}
