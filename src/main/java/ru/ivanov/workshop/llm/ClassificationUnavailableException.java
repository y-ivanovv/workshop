package ru.ivanov.workshop.llm;

/** Сигнал о недоступности классификации; HTTP-слой преобразует его в 503. */
public class ClassificationUnavailableException extends RuntimeException {

    public ClassificationUnavailableException(String message) {
        super(message);
    }
}
