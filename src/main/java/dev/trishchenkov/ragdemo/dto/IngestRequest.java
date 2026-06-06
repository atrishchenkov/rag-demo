package dev.trishchenkov.ragdemo.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record IngestRequest(@NotBlank String text, Map<String, Object> metadata) {}
