package com.example.notes.api;

import com.example.notes.model.Note;

import java.time.Instant;
import java.util.Set;
import java.util.TreeSet;

public record NoteResponse(
        Long id,
        String title,
        String content,
        Long ownerId,
        Long teamId,
        Set<String> tags,
        Instant createdAt,
        Instant updatedAt) {

    public static NoteResponse from(Note n) {
        return new NoteResponse(
                n.getId(),
                n.getTitle(),
                n.getContent(),
                n.getOwner().getId(),
                n.getTeam() == null ? null : n.getTeam().getId(),
                new TreeSet<>(n.getTags()),
                n.getCreatedAt(),
                n.getUpdatedAt());
    }
}
