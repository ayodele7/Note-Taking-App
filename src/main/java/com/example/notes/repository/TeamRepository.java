package com.example.notes.repository;

import com.example.notes.model.Team;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team, Long> {

    boolean existsByIdAndMembersId(Long teamId, Long userId);

    /** Ids of every team the user is a member of. */
    @Query("select t.id from Team t join t.members m where m.id = :userId")
    List<Long> findIdsByMemberId(@Param("userId") Long userId);

    // The two finders below load members up front: open-in-view is off, so the
    // controller can't lazy-load them after the service transaction has closed.

    @EntityGraph(attributePaths = "members")
    Optional<Team> findWithMembersById(Long id);

    @EntityGraph(attributePaths = "members")
    List<Team> findWithMembersByIdInOrderById(Collection<Long> ids);
}
