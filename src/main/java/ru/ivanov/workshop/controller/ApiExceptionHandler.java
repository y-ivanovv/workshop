package ru.ivanov.workshop.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import ru.ivanov.workshop.dto.ApiError;
import ru.ivanov.workshop.llm.ClassificationUnavailableException;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception, Object body, HttpHeaders headers,
            HttpStatusCode status, WebRequest request
    ) {
        ApiError error = switch (status.value()) {
            case 400 -> new ApiError("INVALID_REQUEST",
                    exception instanceof MethodArgumentNotValidException
                            ? "Текст обращения должен содержать от 1 до 2000 символов"
                            : "Некорректный JSON запроса");
            case 404 -> new ApiError("NOT_FOUND", "Запрошенный адрес не найден");
            case 405 -> new ApiError("METHOD_NOT_ALLOWED", "HTTP-метод не поддерживается");
            case 406 -> new ApiError("NOT_ACCEPTABLE", "Ответ доступен только в формате JSON");
            case 415 -> new ApiError("UNSUPPORTED_MEDIA_TYPE", "Используйте Content-Type: application/json");
            case 500 -> new ApiError("INTERNAL_ERROR", "Внутренняя ошибка приложения");
            default -> new ApiError("REQUEST_FAILED", "Не удалось обработать запрос");
        };

        // Сохраняем HTTP-статус и служебные заголовки Spring, например Allow для 405.
        return super.handleExceptionInternal(exception, error, headers, status, request);
    }

    @ExceptionHandler(ClassificationUnavailableException.class)
    public ResponseEntity<ApiError> handleUnavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError("CLASSIFICATION_UNAVAILABLE", "Классификация временно недоступна"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected() {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("INTERNAL_ERROR", "Внутренняя ошибка приложения"));
    }
}
