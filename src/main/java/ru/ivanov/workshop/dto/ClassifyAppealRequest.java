package ru.ivanov.workshop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ClassifyAppealRequest(
        @NotBlank(message = "Текст обращения должен содержать от 1 до 2000 символов")
        @Size(max = 2000, message = "Текст обращения должен содержать от 1 до 2000 символов")
        String text
) {
}
