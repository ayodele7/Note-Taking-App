package com.example.notes.service;

import com.example.notes.api.NoteRequest;
import com.example.notes.api.NoteResponse;
import com.example.notes.api.PageResult;
import com.example.notes.model.Note;
import com.example.notes.model.Team;
import com.example.notes.model.User;
import com.example.notes.repository.NoteRepository;
import com.example.notes.repository.TeamRepository;
import com.example.notes.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * All authorization rules live here:
 * <ul>
 *   <li>A note is visible to its owner and, if it has a team, to every member of that team.</li>
 *   <li>Invisible notes look like they don't exist (404), so ids can't be probed.</li>
 *   <li>Owner or any team member may edit; only the owner may delete (403 for other viewers).</li>
 *   <li>You can only share a note with a team you belong to.</li>
 * </ul>
 */
@Service
@Transactional
public class NoteService {

    static final int MAX_PAGE_SIZE = 100;

    private final NoteRepository notes;
    private final TeamRepository teams;
    private final UserRepository users;

    public NoteService(NoteRepository notes, TeamRepository teams, UserRepository users) {
        this.notes = notes;
        this.teams = teams;
        this.users = users;
    }

    public NoteResponse create(Long callerId, NoteRequest req) {
        User caller = requireUser(callerId);
        Team team = null;
        if (req.teamId() != null) {
            team = teams.findById(req.teamId())
                    .orElseThrow(() -> new NotFoundException("Team not found"));
            if (!teams.existsByIdAndMembersId(team.getId(), callerId)) {
                throw new ForbiddenException("You are not a member of this team");
            }
        }
        Note note = new Note(req.title().trim(), req.content(), caller, team, normalizeTags(req.tags()));
        return NoteResponse.from(notes.save(note));
    }

    @Transactional(readOnly = true)
    public NoteResponse get(Long callerId, Long noteId) {
        requireUser(callerId);
        return NoteResponse.from(requireVisible(callerId, noteId));
    }

    @Transactional(readOnly = true)
    public PageResult<NoteResponse> list(Long callerId, String q, String tag, int page, int size) {
        requireUser(callerId);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "updatedAt"));
        Page<Note> result = notes.findVisible(
                callerId,
                teams.findIdsByMemberId(callerId),
                q == null ? "" : q.trim(),
                tag == null ? "" : tag.trim().toLowerCase(),
                pageable);
        return new PageResult<>(
                result.getContent().stream().map(NoteResponse::from).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements());
    }

    /** Updates title, content and tags. Sharing (team) is fixed at creation time. */
    public NoteResponse update(Long callerId, Long noteId, NoteRequest req) {
        requireUser(callerId);
        Note note = requireVisible(callerId, noteId);
        note.setTitle(req.title().trim());
        note.setContent(req.content());
        note.setTags(normalizeTags(req.tags()));
        return NoteResponse.from(notes.saveAndFlush(note));
    }

    public void delete(Long callerId, Long noteId) {
        requireUser(callerId);
        Note note = requireVisible(callerId, noteId);
        if (!note.getOwner().getId().equals(callerId)) {
            throw new ForbiddenException("Only the owner can delete a note");
        }
        notes.delete(note);
    }

    private User requireUser(Long id) {
        return users.findById(id).orElseThrow(() -> new UnauthorizedException("Unknown user"));
    }

    private Note requireVisible(Long callerId, Long noteId) {
        Note note = notes.findById(noteId).orElseThrow(() -> new NotFoundException("Note not found"));
        boolean isOwner = note.getOwner().getId().equals(callerId);
        boolean isTeamMember = note.getTeam() != null
                && teams.existsByIdAndMembersId(note.getTeam().getId(), callerId);
        if (!isOwner && !isTeamMember) {
            throw new NotFoundException("Note not found");
        }
        return note;
    }

    private static Set<String> normalizeTags(Set<String> tags) {
        if (tags == null) {
            return Collections.emptySet();
        }
        return tags.stream()
                .filter(t -> t != null && !t.isBlank())
                .map(t -> t.trim().toLowerCase())
                .collect(Collectors.toSet());
    }
}
