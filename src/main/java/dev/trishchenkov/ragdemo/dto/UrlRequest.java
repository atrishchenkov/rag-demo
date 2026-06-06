package dev.trishchenkov.ragdemo.dto;

import jakarta.validation.constraints.NotBlank;

public record UrlRequest(@NotBlank String url) {}
