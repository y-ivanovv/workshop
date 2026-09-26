package ru.ivanov.workshop.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import ru.ivanov.workshop.domain.AppealCategory;
import ru.ivanov.workshop.dto.ClassifyAppealRequest;
import ru.ivanov.workshop.llm.ClassificationUnavailableException;
import ru.ivanov.workshop.service.AppealClassificationService;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AppealController.class)
class AppealControllerTest {

    private static final String URL = "/api/appeals/classify";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AppealClassificationService service;

    @Test
    void returnsOnlyCategoryForValidRequest() throws Exception {
        when(service.classify("Нужна карта")).thenReturn(AppealCategory.CARDS);

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Нужна карта\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"category\":\"CARDS\"}", JsonCompareMode.STRICT));

        verify(service).classify("Нужна карта");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}", "{\"text\":null}", "{\"text\":\"\"}", "{\"text\":\"   \"}",
            "{\"text\":\"\\t\\n\"}", "{", "null", "{\"text\":[]}"
    })
    void rejectsInvalidBodyBeforeCallingService(String json) throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").isNotEmpty());

        verifyNoInteractions(service);
    }

    @Test
    void rejectsOversizedTextBeforeTrimming() throws Exception {
        var request = new ClassifyAppealRequest(" ".repeat(2000) + "а");

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {"code":"INVALID_REQUEST","message":"Текст обращения должен содержать от 1 до 2000 символов"}
                        """, JsonCompareMode.STRICT));

        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2000})
    void acceptsTextAtLengthBoundaries(int length) throws Exception {
        String text = "а".repeat(length);
        when(service.classify(text)).thenReturn(AppealCategory.OTHER);

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ClassifyAppealRequest(text))))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"category\":\"OTHER\"}", JsonCompareMode.STRICT));

        verify(service).classify(text);
    }

    @Test
    void rejectsUnsupportedMethodAndPreservesAllowHeader() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW, containsString("POST")))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsUnsupportedContentType() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.TEXT_PLAIN).content("Нужна карта"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));

        verifyNoInteractions(service);
    }

    @Test
    void returnsSafe503ForUnavailableClassification() throws Exception {
        when(service.classify("Нужна карта"))
                .thenThrow(new ClassificationUnavailableException("Внутренние подробности провайдера"));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Нужна карта\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().json("""
                        {"code":"CLASSIFICATION_UNAVAILABLE","message":"Классификация временно недоступна"}
                        """, JsonCompareMode.STRICT));
    }

    @Test
    void returnsSafe500ForUnexpectedError() throws Exception {
        when(service.classify("Нужна карта"))
                .thenThrow(new IllegalStateException("Внутренние подробности и текст: Нужна карта"));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Нужна карта\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().json("""
                        {"code":"INTERNAL_ERROR","message":"Внутренняя ошибка приложения"}
                        """, JsonCompareMode.STRICT));
    }

    @Test
    void preserves404ForUnknownAddress() throws Exception {
        mockMvc.perform(get("/missing-address"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        verifyNoInteractions(service);
    }

    @Test
    void returnsSafe500ForFrameworkResponseError() throws Exception {
        when(service.classify("Нужна карта"))
                .thenThrow(new HttpMessageNotWritableException("Технические подробности ответа"));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Нужна карта\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().json("""
                        {"code":"INTERNAL_ERROR","message":"Внутренняя ошибка приложения"}
                        """, JsonCompareMode.STRICT));
    }
}
