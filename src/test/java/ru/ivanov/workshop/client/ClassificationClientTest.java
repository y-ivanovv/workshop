package ru.ivanov.workshop.client;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.ivanov.workshop.domain.AppealCategory;
import ru.ivanov.workshop.llm.AiGateway;
import ru.ivanov.workshop.llm.LlmRequest;
import ru.ivanov.workshop.llm.LlmResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClassificationClientTest {

    @Test
    void separatesInstructionFromTextAndReturnsCategory() {
        var gateway = mock(AiGateway.class);
        when(gateway.generate(any())).thenReturn(new LlmResponse(AppealCategory.OTHER));
        var client = new ClassificationClient(gateway);
        String text = "Где ближайшее отделение?";

        assertEquals(AppealCategory.OTHER, client.classify(text));

        var requestCaptor = ArgumentCaptor.forClass(LlmRequest.class);
        verify(gateway).generate(requestCaptor.capture());
        var request = requestCaptor.getValue();
        assertEquals(text, request.text());
        assertThat(request.instruction())
                .contains("CREDITS", "CARDS", "TRANSFERS", "OTHER")
                .doesNotContain(text);
    }
}
