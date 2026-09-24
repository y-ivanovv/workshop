package ru.ivanov.workshop.llm;

import org.springframework.stereotype.Component;
import ru.ivanov.workshop.config.LlmProperties;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class AiGateway {

    private final LlmClient activeClient;

    public AiGateway(List<LlmClient> clients, LlmProperties properties) {
        Map<String, LlmClient> providers = new HashMap<>();
        for (LlmClient client : clients) {
            String name = client.providerName();
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Имя LLM-провайдера не должно быть пустым");
            }
            if (providers.putIfAbsent(name, client) != null) {
                throw new IllegalArgumentException("Повторяющееся имя LLM-провайдера: " + name);
            }
        }

        if (!providers.containsKey(properties.activeProvider())) {
            throw new IllegalArgumentException("Неизвестный основной LLM-провайдер: " + properties.activeProvider());
        }
        if (!providers.containsKey(properties.fallbackProvider())) {
            throw new IllegalArgumentException("Неизвестный резервный LLM-провайдер: " + properties.fallbackProvider());
        }
        if (properties.activeProvider().equals(properties.fallbackProvider())) {
            throw new IllegalArgumentException("Основной и резервный LLM-провайдеры должны различаться");
        }

        activeClient = providers.get(properties.activeProvider());
    }

    public LlmResponse generate(LlmRequest request) {
        // На этом этапе только маршрутизация; обработка сбоев появится на этапе 5.
        return activeClient.generate(request);
    }
}
