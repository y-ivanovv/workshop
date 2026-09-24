package ru.ivanov.workshop.llm;

import org.junit.jupiter.api.Test;
import ru.ivanov.workshop.config.LlmProperties;
import ru.ivanov.workshop.domain.AppealCategory;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

class AiGatewayTest {

    @Test
    void forwardsRequestToActiveProviderWithoutCallingReserve() {
        var primary = mock(LlmClient.class);
        var reserve = mock(LlmClient.class);
        when(primary.providerName()).thenReturn("openai");
        when(reserve.providerName()).thenReturn("internal");
        var request = new LlmRequest("Классифицируй обращение", "Кредит");
        var response = new LlmResponse(AppealCategory.CREDITS);
        when(primary.generate(request)).thenReturn(response);
        var gateway = new AiGateway(List.of(primary, reserve), properties());

        assertSame(response, gateway.generate(request));
        verify(primary).generate(request);
        verify(reserve, never()).generate(any());
    }

    @Test
    void rejectsDuplicateProviderNames() {
        var clients = List.<LlmClient>of(
                new OpenAiLlmClient(), new OpenAiLlmClient(), new InternalLlmClient()
        );

        assertThrows(IllegalArgumentException.class, () -> new AiGateway(clients, properties()));
    }

    @Test
    void rejectsProviderWithoutName() {
        var unnamed = mock(LlmClient.class);
        when(unnamed.providerName()).thenReturn(" ");

        assertThrows(IllegalArgumentException.class,
                () -> new AiGateway(List.of(unnamed), properties()));
    }

    private LlmProperties properties() {
        return new LlmProperties("openai", "internal", Duration.ofSeconds(1), 2);
    }
}
