package ru.ivanov.workshop.service;

import org.springframework.stereotype.Service;
import ru.ivanov.workshop.client.ClassificationClient;
import ru.ivanov.workshop.domain.AppealCategory;

@Service
public class AppealClassificationService {

    private final ClassificationClient client;

    public AppealClassificationService(ClassificationClient client) {
        this.client = client;
    }

    public AppealCategory classify(String text) {
        return client.classify(text.strip());
    }
}
