package com.innowise.api_gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "services")
public record ServiceProperties(String authUrl, String userUrl) {
}
