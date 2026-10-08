package com.lifeos.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Shared HTTP client for the outbound AI providers, with a bounded read timeout. */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient.Builder restClientBuilder(AiProperties aiProperties) {
        int timeoutMillis = (int) Math.min(Integer.MAX_VALUE,
                (aiProperties.requestTimeout() == null ? java.time.Duration.ofSeconds(60)
                        : aiProperties.requestTimeout()).toMillis());
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMillis);
        factory.setReadTimeout(timeoutMillis);
        return RestClient.builder().requestFactory(factory);
    }

    @Bean
    @SuppressWarnings("unused")
    public RestTemplateBuilder restTemplateBuilder() {
        return new RestTemplateBuilder();
    }
}