package dev.trishchenkov.ragdemo.dto;

import java.util.List;

public record Answer(String answer, List<String> sources) {}
