package dev.trishchenkov.ragdemo.dto;

import jakarta.validation.constraints.NotBlank;

public record Question(@NotBlank String question, String filter) {}
