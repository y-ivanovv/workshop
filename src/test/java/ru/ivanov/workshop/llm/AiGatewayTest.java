package ru.ivanov.workshop.llm;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import ru.ivanov.workshop.config.LlmExecutionConfiguration;
import ru.ivanov.workshop.config.LlmProperties;
import ru.ivanov.workshop.domain.AppealCategory;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class AiGatewayTest {

    private final ThreadPoolExecutor executor = new LlmExecutionConfiguration().llmExecutor();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final LlmClient primary = mock(LlmClient.class);
    private final LlmClient reserve = mock(LlmClient.class);
    private final LlmRequest request = new LlmRequest("Классифицируй обращение", "Кредит");
    private final LlmResponse response = new LlmResponse(AppealCategory.CREDITS);

    @BeforeEach
    void nameProviders() {
        when(primary.providerName()).thenReturn("openai");
        when(reserve.providerName()).thenReturn("internal");
    }

    @AfterEach
    void closeResources() throws InterruptedException {
        executor.shutdownNow();
        assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS));
        registry.close();
    }

    @ParameterizedTest
    @EnumSource(AppealCategory.class)
    void returnsValidCategoryWithoutRetryOrFallback(AppealCategory category) {
        var expected = new LlmResponse(category);
        when(primary.generate(request)).thenReturn(expected);
        assertSame(expected, gateway().generate(request));
        verify(primary).generate(request);
        verify(reserve, never()).generate(any());
        assertEquals(1, calls("openai", "success"));
        assertEquals(0, fallbacks());
    }

    @Test
    void retriesTemporaryErrorAndReturnsNextSuccess() {
        when(primary.generate(request)).thenThrow(new TemporaryLlmException("Временный сбой")).thenReturn(response);
        assertSame(response, gateway().generate(request));
        verify(primary, times(2)).generate(request);
        verify(reserve, never()).generate(any());
        assertEquals(1, calls("openai", "temporary_error"));
        assertEquals(1, calls("openai", "success"));
        assertEquals(0, fallbacks());
    }

    @Test
    void usesReserveOnceAfterExhaustingPrimaryAttempts() {
        when(primary.generate(request)).thenThrow(new TemporaryLlmException("Временный сбой"));
        when(reserve.generate(request)).thenReturn(response);
        assertSame(response, gateway().generate(request));
        verify(primary, times(2)).generate(request);
        verify(reserve).generate(request);
        assertEquals(2, calls("openai", "temporary_error"));
        assertEquals(1, calls("internal", "success"));
        assertEquals(1, fallbacks());
    }

    @Test
    void skipsRetriesForPermanentError() {
        when(primary.generate(request)).thenThrow(new PermanentLlmException("Постоянный сбой"));
        when(reserve.generate(request)).thenReturn(response);
        assertSame(response, gateway().generate(request));
        verify(primary).generate(request);
        verify(reserve).generate(request);
        assertEquals(1, calls("openai", "permanent_error"));
    }

    @ParameterizedTest
    @MethodSource("invalidResponses")
    void switchesToReserveForInvalidResponse(LlmResponse invalid) {
        when(primary.generate(request)).thenReturn(invalid);
        when(reserve.generate(request)).thenReturn(response);
        assertSame(response, gateway().generate(request));
        verify(primary).generate(request);
        verify(reserve).generate(request);
        assertEquals(1, calls("openai", "invalid_response"));
    }

    static Stream<LlmResponse> invalidResponses() {
        return Stream.of(null, new LlmResponse(null));
    }

    @Test
    void returnsUnavailableWhenReserveAlsoFailsWithoutRetryingIt() {
        when(primary.generate(request)).thenThrow(new TemporaryLlmException("Основной недоступен"));
        when(reserve.generate(request)).thenThrow(new TemporaryLlmException("Резерв недоступен"));
        assertThrows(ClassificationUnavailableException.class, () -> gateway().generate(request));
        verify(primary, times(2)).generate(request);
        verify(reserve).generate(request);
        assertEquals(1, calls("internal", "temporary_error"));
        assertEquals(1, fallbacks());
    }

    @Test
    void rejectsInvalidReserveResponse() {
        when(primary.generate(request)).thenThrow(new PermanentLlmException("Основной недоступен"));
        when(reserve.generate(request)).thenReturn(new LlmResponse(null));
        assertThrows(ClassificationUnavailableException.class, () -> gateway().generate(request));
        verify(reserve).generate(request);
        assertEquals(1, calls("internal", "invalid_response"));
    }

    @Test
    void doesNotHideProgrammingErrorBehindFallback() {
        var bug = new IllegalStateException("Ошибка реализации");
        when(primary.generate(request)).thenThrow(bug);
        assertSame(bug, assertThrows(IllegalStateException.class, () -> gateway().generate(request)));
        verify(primary).generate(request);
        verify(reserve, never()).generate(any());
        assertEquals(1, calls("openai", "unexpected_error"));
        assertEquals(0, fallbacks());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void cancelsSlowTaskAndRetriesOrUsesReserve(boolean retry) throws InterruptedException {
        var entered = new CountDownLatch(1);
        var interrupted = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var invocationNumber = new AtomicInteger();
        when(primary.generate(request)).thenAnswer(invocation -> {
            if (invocationNumber.incrementAndGet() > 1) {
                return response;
            }
            entered.countDown();
            try {
                release.await();
            } catch (InterruptedException exception) {
                interrupted.countDown();
                Thread.currentThread().interrupt();
            }
            return response;
        });
        when(reserve.generate(request)).thenReturn(response);
        try {
            assertSame(response, gateway(Duration.ofSeconds(1), retry ? 2 : 1).generate(request));
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            assertTrue(interrupted.await(3, TimeUnit.SECONDS));
            assertEquals(1, calls("openai", "timeout"));
            assertEquals(1, calls(retry ? "openai" : "internal", "success"));
            assertEquals(retry ? 0 : 1, fallbacks());
        } finally {
            release.countDown();
        }
    }

    @Test
    void timeoutIncludesQueueWaitingAndRemovesCancelledTasks() throws InterruptedException {
        CountDownLatch release = occupyWorkers(false);
        try {
            assertThrows(ClassificationUnavailableException.class, () -> gateway(Duration.ofMillis(100), 1).generate(request));
            verify(primary, never()).generate(any());
            verify(reserve, never()).generate(any());
            assertEquals(1, calls("openai", "timeout"));
            assertEquals(1, calls("internal", "timeout"));
            assertTrue(executor.getQueue().isEmpty());
        } finally {
            release.countDown();
        }
    }

    @Test
    void queueOverflowDoesNotRetryPrimary() throws InterruptedException {
        CountDownLatch release = occupyWorkers(true);
        try {
            assertThrows(ClassificationUnavailableException.class, () -> gateway().generate(request));
            verify(primary, never()).generate(any());
            verify(reserve, never()).generate(any());
            assertEquals(1, calls("openai", "rejected"));
            assertEquals(1, calls("internal", "rejected"));
            assertEquals(1, fallbacks());
        } finally {
            release.countDown();
        }
    }

    @Test
    void preInterruptedCallerStartsNoTasks() {
        var gateway = gateway();
        try {
            Thread.currentThread().interrupt();
            assertThrows(ClassificationUnavailableException.class, () -> gateway.generate(request));
            assertTrue(Thread.currentThread().isInterrupted());
            verify(primary, never()).generate(any());
            verify(reserve, never()).generate(any());
            assertEquals(0, fallbacks());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void interruptedCallerCancelsProviderAndStopsWithoutFallback() throws InterruptedException {
        var entered = new CountDownLatch(1);
        var cancelled = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var finished = new CountDownLatch(1);
        var failure = new AtomicReference<ClassificationUnavailableException>();
        var interruptRestored = new AtomicBoolean();
        when(primary.generate(request)).thenAnswer(invocation -> {
            entered.countDown();
            try {
                release.await();
            } catch (InterruptedException exception) {
                cancelled.countDown();
                Thread.currentThread().interrupt();
            }
            return response;
        });
        var gateway = gateway(Duration.ofSeconds(5), 2);
        var caller = new Thread(() -> {
            try {
                gateway.generate(request);
            } catch (ClassificationUnavailableException exception) {
                failure.set(exception);
            } finally {
                interruptRestored.set(Thread.currentThread().isInterrupted());
                finished.countDown();
            }
        });
        try {
            caller.start();
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            caller.interrupt();
            assertTrue(finished.await(3, TimeUnit.SECONDS));
            assertTrue(cancelled.await(3, TimeUnit.SECONDS));
            assertThat(failure.get()).isNotNull();
            assertTrue(interruptRestored.get());
            verify(primary).generate(request);
            verify(reserve, never()).generate(any());
            assertEquals(1, calls("openai", "interrupted"));
            assertEquals(0, fallbacks());
        } finally {
            release.countDown();
            caller.interrupt();
            caller.join(3000);
            assertFalse(caller.isAlive());
        }
    }

    @Test
    void interruptionIsNotLostWhenProviderCompletesAtTheSameTime() {
        Thread caller = Thread.currentThread();
        when(primary.generate(request)).thenAnswer(invocation -> {
            caller.interrupt();
            return response;
        });
        try {
            assertThrows(ClassificationUnavailableException.class, () -> gateway().generate(request));
            assertTrue(Thread.currentThread().isInterrupted());
            verify(reserve, never()).generate(any());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void logsAndMetricsExcludePayloadAndExceptionDetails(CapturedOutput output) {
        String marker = "private-marker-5ca7";
        var privateRequest = new LlmRequest(marker, marker);
        when(primary.generate(privateRequest)).thenThrow(new PermanentLlmException(marker));
        when(reserve.generate(privateRequest)).thenReturn(response);
        gateway().generate(privateRequest);
        assertThat(output.getAll()).contains("outcome=permanent_error", "LLM fallback").doesNotContain(marker, "category=");
        assertEquals(1, registry.get("llm.call.duration").tags("provider", "openai", "outcome", "permanent_error").timer().count());
        assertEquals(1, registry.get("llm.call.duration").tags("provider", "internal", "outcome", "success").timer().count());
        registry.getMeters().forEach(meter -> {
            assertThat(meter.getId().toString()).doesNotContain(marker);
            meter.getId().getTags().forEach(tag -> assertThat(tag.getKey()).isIn("provider", "outcome"));
        });
    }

    @Test
    void rejectsDuplicateProviderNames() {
        var clients = List.<LlmClient>of(new OpenAiLlmClient(), new OpenAiLlmClient(), new InternalLlmClient());
        assertThrows(IllegalArgumentException.class,
                () -> new AiGateway(clients, properties(Duration.ofSeconds(1), 2), executor, registry));
    }

    @Test
    void rejectsProviderWithoutName() {
        when(primary.providerName()).thenReturn(" ");
        assertThrows(IllegalArgumentException.class, this::gateway);
    }

    private CountDownLatch occupyWorkers(boolean fillQueue) throws InterruptedException {
        var entered = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        Runnable blocker = () -> {
            entered.countDown();
            try {
                release.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        };
        executor.execute(blocker);
        executor.execute(blocker);
        assertTrue(entered.await(3, TimeUnit.SECONDS));
        if (fillQueue) {
            for (int index = 0; index < 10; index++) {
                executor.execute(blocker);
            }
        }
        return release;
    }

    private AiGateway gateway() {
        return gateway(Duration.ofSeconds(2), 2);
    }

    private AiGateway gateway(Duration timeout, int maxAttempts) {
        return new AiGateway(List.of(primary, reserve), properties(timeout, maxAttempts), executor, registry);
    }

    private LlmProperties properties(Duration timeout, int maxAttempts) {
        return new LlmProperties("openai", "internal", timeout, maxAttempts);
    }

    private double calls(String provider, String outcome) {
        var counter = registry.find("llm.calls").tags("provider", provider, "outcome", outcome).counter();
        return counter == null ? 0 : counter.count();
    }

    private double fallbacks() {
        return registry.get("llm.fallbacks").counter().count();
    }
}
