package ru.ivanov.workshop.llm;

/** Инструкция приложения и текст клиента передаются раздельно. */
public record LlmRequest(String instruction, String text) {
}
