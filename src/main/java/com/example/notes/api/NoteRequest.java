package com.example.notes.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record NoteRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 10000) String content,
        Long teamId,
        @Size(max = 20) Set<@Size(max = 50) String> tags) {
}
