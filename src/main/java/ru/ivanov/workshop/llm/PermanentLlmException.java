package ru.ivanov.workshop.llm;

/** Постоянная ошибка: повтор у того же провайдера не нужен. */
public class PermanentLlmException extends RuntimeException {

    public PermanentLlmException(String message) {
        super(message);
    }
}
