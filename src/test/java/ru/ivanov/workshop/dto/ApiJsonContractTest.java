package ru.ivanov.workshop.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.ivanov.workshop.domain.AppealCategory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ApiJsonContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void readsRequestWithoutChangingOriginalText() throws Exception {
        var request = objectMapper.readValue(
                """
                {"text":"  Как погасить кредит?  "}
                """,
                ClassifyAppealRequest.class
        );

        assertEquals("  Как погасить кредит?  ", request.text());
    }

    @Test
    void leavesMissingTextForValidation() throws Exception {
        var request = objectMapper.readValue("{}", ClassifyAppealRequest.class);

        assertNull(request.text());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CREDITS", "CARDS", "TRANSFERS", "OTHER"})
    void writesOnlyPublicCategoryField(String categoryName) throws Exception {
        var response = new ClassifyAppealResponse(AppealCategory.valueOf(categoryName));
        var expectedJson = objectMapper.readTree("{\"category\":\"" + categoryName + "\"}");
        var actualJson = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertEquals(expectedJson, actualJson);
    }

    @Test
    void writesErrorCodeAndMessage() throws Exception {
        var error = new ApiError("INVALID_REQUEST", "Текст обращения обязателен");
        var expectedJson = objectMapper.readTree(
                """
                {"code":"INVALID_REQUEST","message":"Текст обращения обязателен"}
                """
        );
        var actualJson = objectMapper.readTree(objectMapper.writeValueAsString(error));

        assertEquals(expectedJson, actualJson);
    }
}
