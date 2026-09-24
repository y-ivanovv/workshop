package ru.ivanov.workshop.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "llm")
public record LlmProperties(
        String activeProvider,
        String fallbackProvider,
        Duration timeout,
        int maxAttempts
) {
    public LlmProperties {
        if (activeProvider == null || activeProvider.isBlank()) {
            throw new IllegalArgumentException("llm.active-provider должен быть задан");
        }
        if (fallbackProvider == null || fallbackProvider.isBlank()) {
            throw new IllegalArgumentException("llm.fallback-provider должен быть задан");
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("llm.timeout должен быть положительным");
        }
        if (maxAttempts < 1 || maxAttempts > 3) {
            throw new IllegalArgumentException("llm.max-attempts должен быть от 1 до 3");
        }
    }
}
