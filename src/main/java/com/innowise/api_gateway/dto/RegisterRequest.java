package com.innowise.api_gateway.dto;

import java.time.LocalDate;

public record RegisterRequest(String email, String password, String name, String surname, LocalDate birthDate) {
}
