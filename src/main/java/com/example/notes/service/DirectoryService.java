package com.example.notes.service;

import com.example.notes.model.Team;
import com.example.notes.model.User;
import com.example.notes.repository.TeamRepository;
import com.example.notes.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Minimal user/team management so the service is usable end to end without a separate auth system. */
@Service
@Transactional
public class DirectoryService {

    private final UserRepository users;
    private final TeamRepository teams;

    public DirectoryService(UserRepository users, TeamRepository teams) {
        this.users = users;
        this.teams = teams;
    }

    public User createUser(String name) {
        return users.save(new User(name.trim()));
    }

    /** The creator becomes the first member. */
    public Team createTeam(Long callerId, String name) {
        User caller = users.findById(callerId).orElseThrow(() -> new UnauthorizedException("Unknown user"));
        Team team = new Team(name.trim());
        team.getMembers().add(caller);
        return teams.save(team);
    }

    /** Only existing members can add new members. */
    public Team addMember(Long callerId, Long teamId, Long newMemberId) {
        users.findById(callerId).orElseThrow(() -> new UnauthorizedException("Unknown user"));
        Team team = teams.findById(teamId).orElseThrow(() -> new NotFoundException("Team not found"));
        if (!teams.existsByIdAndMembersId(teamId, callerId)) {
            throw new ForbiddenException("Only team members can add members");
        }
        User newMember = users.findById(newMemberId).orElseThrow(() -> new NotFoundException("User not found"));
        team.getMembers().add(newMember);
        return teams.save(team);
    }

    @Transactional(readOnly = true)
    public User me(Long callerId) {
        return requireUser(callerId);
    }

    /** Teams the caller belongs to, ordered by id. */
    @Transactional(readOnly = true)
    public List<Team> myTeams(Long callerId) {
        requireUser(callerId);
        return teams.findWithMembersByIdInOrderById(teams.findIdsByMemberId(callerId));
    }

    /** Members only; to anyone else the team looks like it doesn't exist (404), as with notes. */
    @Transactional(readOnly = true)
    public Team getTeam(Long callerId, Long teamId) {
        requireUser(callerId);
        if (!teams.existsByIdAndMembersId(teamId, callerId)) {
            throw new NotFoundException("Team not found");
        }
        return teams.findWithMembersById(teamId).orElseThrow(() -> new NotFoundException("Team not found"));
    }

    private User requireUser(Long id) {
        return users.findById(id).orElseThrow(() -> new UnauthorizedException("Unknown user"));
    }
}
