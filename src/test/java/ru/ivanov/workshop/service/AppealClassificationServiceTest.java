package ru.ivanov.workshop.service;

import org.junit.jupiter.api.Test;
import ru.ivanov.workshop.client.ClassificationClient;
import ru.ivanov.workshop.domain.AppealCategory;
import ru.ivanov.workshop.llm.ClassificationUnavailableException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppealClassificationServiceTest {

    private final ClassificationClient client = mock(ClassificationClient.class);
    private final AppealClassificationService service = new AppealClassificationService(client);

    @Test
    void stripsSurroundingWhitespaceBeforeCallingClient() {
        when(client.classify("Нужна карта")).thenReturn(AppealCategory.CARDS);

        var category = service.classify(" \nНужна карта\t  ");

        assertEquals(AppealCategory.CARDS, category);
        verify(client).classify("Нужна карта");
    }

    @Test
    void propagatesUnavailabilityInsteadOfReturningOther() {
        var unavailable = new ClassificationUnavailableException("Провайдер недоступен");
        when(client.classify("Нужна карта")).thenThrow(unavailable);

        var actual = assertThrows(ClassificationUnavailableException.class,
                () -> service.classify("Нужна карта"));

        assertSame(unavailable, actual);
    }
}
