package ru.ivanov.workshop.llm;

/** Ответ провайдера не соответствует контракту классификации. */
public class InvalidLlmResponseException extends RuntimeException {

    public InvalidLlmResponseException(String message) {
        super(message);
    }
}
