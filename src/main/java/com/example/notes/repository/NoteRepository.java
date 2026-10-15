package com.example.notes.repository;

import com.example.notes.model.Note;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NoteRepository extends JpaRepository<Note, Long> {

    /**
     * Notes the user may see: their own, plus any shared with one of {@code teamIds}
     * (the caller's teams, looked up first via {@link TeamRepository#findIdsByMemberId}).
     * Empty-string q / tag mean "no filter" (avoids typed-null parameter issues).
     */
    @Query("""
            select n from Note n
            where (n.owner.id = :userId or n.team.id in :teamIds)
              and (:q = '' or lower(n.title) like lower(concat('%', :q, '%'))
                           or lower(n.content) like lower(concat('%', :q, '%')))
              and (:tag = '' or :tag member of n.tags)
            """)
    Page<Note> findVisible(@Param("userId") Long userId,
                           @Param("teamIds") List<Long> teamIds,
                           @Param("q") String q,
                           @Param("tag") String tag,
                           Pageable pageable);
}
