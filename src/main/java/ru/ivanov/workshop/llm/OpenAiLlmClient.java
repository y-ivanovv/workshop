package ru.ivanov.workshop.llm;

import org.springframework.stereotype.Component;
import ru.ivanov.workshop.domain.AppealCategory;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CancellationException;

/** Локальная эмуляция: запросы в OpenAI не отправляются, API-ключ не нужен. */
@Component
public class OpenAiLlmClient implements LlmClient {

    private static final Set<String> CREDIT_WORDS = Set.of(
            "кредит", "кредита", "кредиту", "кредитом", "кредите", "кредиты",
            "кредитов", "кредитам", "кредитами", "кредитах",
            "ипотека", "ипотеки", "ипотеку", "ипотекой", "ипотеке"
    );
    private static final Set<String> CARD_WORDS = Set.of(
            "карта", "карты", "карте", "карту", "картой", "карт", "картам", "картами", "картах",
            "карточка", "карточки", "карточке", "карточку", "карточкой"
    );
    private static final Set<String> TRANSFER_WORDS = Set.of(
            "перевод", "перевода", "переводу", "переводом", "переводе", "переводы",
            "переводов", "переводам", "переводами", "переводах", "перевести"
    );

    @Override
    public String providerName() {
        return "openai";
    }

    @Override
    public LlmResponse generate(LlmRequest request) {
        boolean credits = false;
        boolean cards = false;
        boolean transfers = false;

        // Проверяем целые слова: «картина» и «переводчик» не являются ключевыми словами.
        for (String word : request.text().toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}_]+")) {
            if (Thread.currentThread().isInterrupted()) {
                throw new CancellationException("Вызов LLM прерван");
            }
            credits = credits || CREDIT_WORDS.contains(word);
            cards = cards || CARD_WORDS.contains(word);
            transfers = transfers || TRANSFER_WORDS.contains(word);
        }

        int matchedCategories = (credits ? 1 : 0) + (cards ? 1 : 0) + (transfers ? 1 : 0);
        if (matchedCategories != 1) {
            return new LlmResponse(AppealCategory.OTHER);
        }
        if (credits) {
            return new LlmResponse(AppealCategory.CREDITS);
        }
        if (cards) {
            return new LlmResponse(AppealCategory.CARDS);
        }
        return new LlmResponse(AppealCategory.TRANSFERS);
    }
}
