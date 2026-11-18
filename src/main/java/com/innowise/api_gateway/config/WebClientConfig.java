package com.innowise.api_gateway.config;

import io.netty.channel.ChannelOption;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.cloud.gateway.config.HttpClientProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.netty.http.client.HttpClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClientCustomizer timeoutCustomizer(HttpClientProperties properties) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.getConnectTimeout())
                .responseTimeout(properties.getResponseTimeout());
        return builder -> builder.clientConnector(new ReactorClientHttpConnector(httpClient));
    }
}
