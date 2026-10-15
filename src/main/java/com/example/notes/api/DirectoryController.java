package com.example.notes.api;

import com.example.notes.model.Team;
import com.example.notes.model.User;
import com.example.notes.service.DirectoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@RestController
public class DirectoryController {

    public record NameRequest(@NotBlank @Size(max = 100) String name) {
    }

    public record UserResponse(Long id, String name) {
    }

    public record TeamResponse(Long id, String name, Set<Long> memberIds) {
    }

    private final DirectoryService service;

    public DirectoryController(DirectoryService service) {
        this.service = service;
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createUser(@Valid @RequestBody NameRequest req) {
        User u = service.createUser(req.name());
        return new UserResponse(u.getId(), u.getName());
    }

    @PostMapping("/teams")
    @ResponseStatus(HttpStatus.CREATED)
    public TeamResponse createTeam(@RequestHeader(NoteController.USER_HEADER) Long userId,
                                   @Valid @RequestBody NameRequest req) {
        return toResponse(service.createTeam(userId, req.name()));
    }

    @PutMapping("/teams/{teamId}/members/{userId}")
    public TeamResponse addMember(@RequestHeader(NoteController.USER_HEADER) Long callerId,
                                  @PathVariable Long teamId,
                                  @PathVariable Long userId) {
        return toResponse(service.addMember(callerId, teamId, userId));
    }

    @GetMapping("/users/me")
    public UserResponse me(@RequestHeader(NoteController.USER_HEADER) Long userId) {
        User u = service.me(userId);
        return new UserResponse(u.getId(), u.getName());
    }

    @GetMapping("/teams")
    public List<TeamResponse> myTeams(@RequestHeader(NoteController.USER_HEADER) Long userId) {
        return service.myTeams(userId).stream().map(DirectoryController::toResponse).toList();
    }

    @GetMapping("/teams/{teamId}")
    public TeamResponse getTeam(@RequestHeader(NoteController.USER_HEADER) Long userId,
                                @PathVariable Long teamId) {
        return toResponse(service.getTeam(userId, teamId));
    }

    private static TeamResponse toResponse(Team t) {
        Set<Long> ids = t.getMembers().stream().map(User::getId).collect(Collectors.toCollection(TreeSet::new));
        return new TeamResponse(t.getId(), t.getName(), ids);
    }
}
