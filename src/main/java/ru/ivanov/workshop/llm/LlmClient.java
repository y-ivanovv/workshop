package ru.ivanov.workshop.llm;

/** Общий контракт провайдера, не зависящий от его API или SDK. */
public interface LlmClient {

    String providerName();

    LlmResponse generate(LlmRequest request);
}
