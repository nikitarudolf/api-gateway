package com.innowise.api_gateway.dto;

import java.time.LocalDate;

public record UserRequest(String name, String surname, LocalDate birthDate, String email) {
}
