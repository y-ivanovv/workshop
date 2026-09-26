package ru.ivanov.workshop.controller;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.ivanov.workshop.dto.ClassifyAppealRequest;
import ru.ivanov.workshop.dto.ClassifyAppealResponse;
import ru.ivanov.workshop.service.AppealClassificationService;

@RestController
@RequestMapping("/api/appeals")
public class AppealController {

    private final AppealClassificationService service;

    public AppealController(AppealClassificationService service) {
        this.service = service;
    }

    @PostMapping(value = "/classify", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ClassifyAppealResponse classify(@Valid @RequestBody ClassifyAppealRequest request) {
        return new ClassifyAppealResponse(service.classify(request.text()));
    }
}
