package ru.ivanov.workshop.llm;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.ivanov.workshop.config.LlmProperties;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class AiGateway {

    private static final Logger log = LoggerFactory.getLogger(AiGateway.class);

    private final LlmClient activeClient;
    private final LlmClient fallbackClient;
    private final LlmProperties properties;
    private final ThreadPoolExecutor executor;
    private final MeterRegistry meterRegistry;
    private final Counter fallbackCounter;
    private final long timeoutNanos;

    public AiGateway(List<LlmClient> clients, LlmProperties properties,
                     ThreadPoolExecutor executor, MeterRegistry meterRegistry) {
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
        fallbackClient = providers.get(properties.fallbackProvider());
        this.properties = properties;
        this.executor = executor;
        this.meterRegistry = meterRegistry;
        fallbackCounter = meterRegistry.counter("llm.fallbacks");
        timeoutNanos = properties.timeout().toNanos();
    }

    public LlmResponse generate(LlmRequest request) {
        ensureNotInterrupted();
        for (int attempt = 1; attempt <= properties.maxAttempts(); attempt++) {
            try {
                return invoke(activeClient, properties.activeProvider(), attempt, request);
            } catch (TemporaryLlmException | TimeoutException exception) {
                // Только эти ошибки разрешают ещё одну попытку основного провайдера.
            } catch (PermanentLlmException | InvalidLlmResponseException | RejectedExecutionException exception) {
                break;
            }
        }

        ensureNotInterrupted();
        fallbackCounter.increment();
        log.info("LLM fallback provider={}", properties.fallbackProvider());
        try {
            return invoke(fallbackClient, properties.fallbackProvider(), 1, request);
        } catch (TemporaryLlmException | PermanentLlmException | InvalidLlmResponseException
                 | RejectedExecutionException | TimeoutException exception) {
            throw new ClassificationUnavailableException("Классификация временно недоступна");
        }
    }

    private LlmResponse invoke(LlmClient client, String provider, int attempt, LlmRequest request)
            throws TimeoutException {
        ensureNotInterrupted();
        long started = System.nanoTime();
        String outcome = "unexpected_error";
        Future<LlmResponse> task = null;
        try {
            task = executor.submit(() -> client.generate(request));
            LlmResponse response = task.get(timeoutNanos, TimeUnit.NANOSECONDS);
            if (Thread.currentThread().isInterrupted()) {
                outcome = "interrupted";
                cancel(task);
                throw new ClassificationUnavailableException("Обработка классификации прервана");
            }
            if (response == null || response.category() == null) {
                outcome = "invalid_response";
                throw new InvalidLlmResponseException("Провайдер вернул некорректный ответ");
            }
            outcome = "success";
            return response;
        } catch (TimeoutException exception) {
            outcome = "timeout";
            cancel(task);
            throw exception;
        } catch (InterruptedException exception) {
            outcome = "interrupted";
            cancel(task);
            Thread.currentThread().interrupt();
            throw new ClassificationUnavailableException("Ожидание классификации прервано");
        } catch (CancellationException exception) {
            outcome = "interrupted";
            throw new ClassificationUnavailableException("Вызов классификации отменён");
        } catch (RejectedExecutionException exception) {
            outcome = "rejected";
            throw exception;
        } catch (ExecutionException exception) {
            // Future оборачивает исключение провайдера; разбираем только известные виды сбоев.
            Throwable cause = exception.getCause();
            if (cause instanceof TemporaryLlmException temporary) {
                outcome = "temporary_error";
                throw temporary;
            }
            if (cause instanceof PermanentLlmException permanent) {
                outcome = "permanent_error";
                throw permanent;
            }
            if (cause instanceof InvalidLlmResponseException invalid) {
                outcome = "invalid_response";
                throw invalid;
            }
            if (cause instanceof CancellationException) {
                outcome = "interrupted";
                throw new ClassificationUnavailableException("Вызов классификации отменён");
            }
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Неожиданная ошибка выполнения LLM", cause);
        } finally {
            long elapsed = System.nanoTime() - started;
            meterRegistry.counter("llm.calls", "provider", provider, "outcome", outcome).increment();
            meterRegistry.timer("llm.call.duration", "provider", provider, "outcome", outcome)
                    .record(elapsed, TimeUnit.NANOSECONDS);
            log.info("LLM provider={} attempt={} outcome={} duration_ms={}",
                    provider, attempt, outcome, TimeUnit.NANOSECONDS.toMillis(elapsed));
        }
    }

    private void cancel(Future<?> task) {
        if (task != null) {
            task.cancel(true);
            // Отменённая задача не должна занимать место в ограниченной очереди.
            if (task instanceof Runnable runnable) {
                executor.remove(runnable);
            }
        }
    }

    private void ensureNotInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw new ClassificationUnavailableException("Обработка классификации прервана");
        }
    }
}
