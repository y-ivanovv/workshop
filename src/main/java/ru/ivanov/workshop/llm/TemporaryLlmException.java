package ru.ivanov.workshop.llm;

/** Временная ошибка провайдера: вызов разрешено повторить. */
public class TemporaryLlmException extends RuntimeException {

    public TemporaryLlmException(String message) {
        super(message);
    }
}
