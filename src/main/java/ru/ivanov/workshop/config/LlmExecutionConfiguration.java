package ru.ivanov.workshop.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration(proxyBeanMethods = false)
public class LlmExecutionConfiguration {

    @Bean(destroyMethod = "shutdownNow")
    public ThreadPoolExecutor llmExecutor() {
        return new ThreadPoolExecutor(
                2, 2, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(10), new ThreadPoolExecutor.AbortPolicy()
        );
    }
}
