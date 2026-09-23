package ru.ivanov.workshop.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassifyAppealRequestTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", " \t\n"})
    void rejectsMissingOrBlankText(String text) {
        var violations = validator.validate(new ClassifyAppealRequest(text));

        assertFalse(violations.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2000})
    void acceptsTextAtLengthBoundaries(int length) {
        var request = new ClassifyAppealRequest("а".repeat(length));

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void rejectsTextOverLimitBeforeTrimming() {
        var request = new ClassifyAppealRequest(" ".repeat(2000) + "а");

        assertFalse(validator.validate(request).isEmpty());
    }
}
