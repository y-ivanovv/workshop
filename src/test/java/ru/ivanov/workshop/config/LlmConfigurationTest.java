package ru.ivanov.workshop.config;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import ru.ivanov.workshop.domain.AppealCategory;
import ru.ivanov.workshop.llm.AiGateway;
import ru.ivanov.workshop.llm.InternalLlmClient;
import ru.ivanov.workshop.llm.LlmRequest;
import ru.ivanov.workshop.llm.OpenAiLlmClient;

import static org.assertj.core.api.Assertions.assertThat;

class LlmConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class)
            .withPropertyValues(
                    "llm.active-provider=openai",
                    "llm.fallback-provider=internal",
                    "llm.timeout=1s",
                    "llm.max-attempts=2"
            );

    @ParameterizedTest
    @CsvSource({"openai,internal,CREDITS", "internal,openai,OTHER"})
    void selectsProviderUsingConfiguration(String active, String fallback, AppealCategory expected) {
        contextRunner.withPropertyValues(
                "llm.active-provider=" + active,
                "llm.fallback-provider=" + fallback
        ).run(context -> {
            assertThat(context).hasNotFailed();
            var gateway = context.getBean(AiGateway.class);
            var response = gateway.generate(new LlmRequest("Классифицируй обращение", "Нужна ипотека"));

            assertThat(response.category()).isEqualTo(expected);
            assertThat(context.getBean(LlmProperties.class).timeout()).hasSeconds(1);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "llm.active-provider=missing",
            "llm.fallback-provider=missing",
            "llm.fallback-provider=openai",
            "llm.active-provider=",
            "llm.fallback-provider=",
            "llm.timeout=0s",
            "llm.timeout=-1s",
            "llm.timeout=not-a-duration",
            "llm.max-attempts=0",
            "llm.max-attempts=4"
    })
    void rejectsInvalidConfigurationAtStartup(String invalidProperty) {
        contextRunner.withPropertyValues(invalidProperty).run(context ->
                assertThat(context).hasFailed()
        );
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(LlmProperties.class)
    @Import({AiGateway.class, OpenAiLlmClient.class, InternalLlmClient.class})
    static class TestConfiguration {
    }
}
