package ru.ivanov.workshop.llm;

import org.springframework.stereotype.Component;
import ru.ivanov.workshop.domain.AppealCategory;

import java.util.Locale;

/** Независимая локальная заглушка с тремя базовыми ключевыми словами. */
@Component
public class InternalLlmClient implements LlmClient {

    @Override
    public String providerName() {
        return "internal";
    }

    @Override
    public LlmResponse generate(LlmRequest request) {
        boolean credits = false;
        boolean cards = false;
        boolean transfers = false;

        for (String word : request.text().toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}_]+")) {
            switch (word) {
                case "кредит" -> credits = true;
                case "карта" -> cards = true;
                case "перевод" -> transfers = true;
            }
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
