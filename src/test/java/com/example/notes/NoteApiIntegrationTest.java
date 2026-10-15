package com.example.notes;

import com.example.notes.repository.NoteRepository;
import com.example.notes.repository.TeamRepository;
import com.example.notes.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests through the HTTP layer. Deliberately not @Transactional: each request
 * commits on its own, as in production, so lazy-loading bugs can't hide behind an open
 * test transaction. Tables are wiped before each test instead.
 */
@SpringBootTest
@AutoConfigureMockMvc
class NoteApiIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    NoteRepository noteRepository;
    @Autowired
    TeamRepository teamRepository;
    @Autowired
    UserRepository userRepository;

    long alice;
    long bob;
    long carol;
    long team; // alice + bob

    @BeforeEach
    void setUp() throws Exception {
        noteRepository.deleteAll();
        teamRepository.deleteAll();
        userRepository.deleteAll();

        alice = createUser("Alice");
        bob = createUser("Bob");
        carol = createUser("Carol");
        team = createTeam(alice, "Platform");
        mvc.perform(put("/teams/{t}/members/{u}", team, bob).header("X-User-Id", alice))
                .andExpect(status().isOk());
    }

    // ---- access rules ----

    @Test
    void nonMemberCannotAddTeamMembers() throws Exception {
        mvc.perform(put("/teams/{t}/members/{u}", team, carol).header("X-User-Id", carol))
                .andExpect(status().isForbidden());
        mvc.perform(get("/teams/{t}", team).header("X-User-Id", alice))
                .andExpect(jsonPath("$.memberIds", hasSize(2)));
    }

    @Test
    void outsiderDeletingTeamNoteGets404AndNoteSurvives() throws Exception {
        long id = createNote(alice, "Shared", "x", team, "[]");

        mvc.perform(delete("/notes/{id}", id).header("X-User-Id", carol)).andExpect(status().isNotFound());
        mvc.perform(get("/notes/{id}", id).header("X-User-Id", alice)).andExpect(status().isOk());
    }

    @Test
    void memberOfTwoTeamsSeesNotesFromBoth() throws Exception {
        long design = createTeam(carol, "Design");
        mvc.perform(put("/teams/{t}/members/{u}", design, alice).header("X-User-Id", carol))
                .andExpect(status().isOk());
        createNote(bob, "Platform note", "x", team, "[]");
        createNote(carol, "Design note", "x", design, "[]");

        mvc.perform(get("/notes").header("X-User-Id", alice)).andExpect(jsonPath("$.total").value(2));
        mvc.perform(get("/notes").header("X-User-Id", bob)).andExpect(jsonPath("$.total").value(1));
    }

    // ---- listing ----

    @Test
    void listIsOrderedByMostRecentlyUpdated() throws Exception {
        long first = createNote(alice, "First", "x", null, "[]");
        createNote(alice, "Second", "x", null, "[]");

        mvc.perform(get("/notes").header("X-User-Id", alice))
                .andExpect(jsonPath("$.items[0].title").value("Second"));

        mvc.perform(put("/notes/{id}", first).header("X-User-Id", alice).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"First, edited\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/notes").header("X-User-Id", alice))
                .andExpect(jsonPath("$.items[0].title").value("First, edited"));
    }

    @Test
    void pageSizeIsClampedToAllowedRange() throws Exception {
        mvc.perform(get("/notes").header("X-User-Id", alice).param("size", "500"))
                .andExpect(jsonPath("$.size").value(100));
        mvc.perform(get("/notes").header("X-User-Id", alice).param("size", "0"))
                .andExpect(jsonPath("$.size").value(1));
    }

    @Test
    void tagFilterIgnoresCaseAndCombinesWithSearch() throws Exception {
        createNote(alice, "Sprint planning", "estimate stories", null, "[\"work\"]");
        createNote(alice, "Retro", "what went well", null, "[\"work\"]");
        createNote(alice, "Retro at home", "family meeting", null, "[\"home\"]");

        mvc.perform(get("/notes").header("X-User-Id", alice).param("tag", "WORK"))
                .andExpect(jsonPath("$.total").value(2));
        mvc.perform(get("/notes").header("X-User-Id", alice).param("tag", "work").param("q", "retro"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].title").value("Retro"));
    }

    // ---- updates ----

    @Test
    void updateReplacesTagsRatherThanMerging() throws Exception {
        long id = createNote(alice, "Tagged", "x", null, "[\"a\",\"b\"]");

        mvc.perform(put("/notes/{id}", id).header("X-User-Id", alice).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Tagged\",\"tags\":[\"c\"]}"))
                .andExpect(jsonPath("$.tags", hasSize(1)))
                .andExpect(jsonPath("$.tags[0]").value("c"));
        mvc.perform(put("/notes/{id}", id).header("X-User-Id", alice).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Tagged\"}"))
                .andExpect(jsonPath("$.tags", hasSize(0)));
    }

    // ---- existing scenarios ----

    @Test
    void privateNoteIsOnlyVisibleToOwner() throws Exception {
        long id = createNote(alice, "Secret", "mine", null, "[]");

        mvc.perform(get("/notes/{id}", id).header("X-User-Id", alice)).andExpect(status().isOk());
        mvc.perform(get("/notes/{id}", id).header("X-User-Id", bob)).andExpect(status().isNotFound());
        mvc.perform(get("/notes").header("X-User-Id", bob))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void teamNoteIsVisibleAndEditableByMembersButNotOutsiders() throws Exception {
        long id = createNote(alice, "Standup", "notes", team, "[\"work\"]");

        mvc.perform(get("/notes/{id}", id).header("X-User-Id", bob))
                .andExpect(status().isOk()).andExpect(jsonPath("$.teamId").value(team));
        mvc.perform(get("/notes/{id}", id).header("X-User-Id", carol)).andExpect(status().isNotFound());

        mvc.perform(put("/notes/{id}", id).header("X-User-Id", bob).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Standup v2\",\"content\":\"edited by bob\",\"tags\":[\"Work\",\"daily\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Standup v2"))
                .andExpect(jsonPath("$.ownerId").value(alice))
                .andExpect(jsonPath("$.tags", hasSize(2)));

        mvc.perform(put("/notes/{id}", id).header("X-User-Id", carol).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"hax\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void onlyOwnerCanDelete() throws Exception {
        long id = createNote(alice, "Shared", "x", team, "[]");

        mvc.perform(delete("/notes/{id}", id).header("X-User-Id", bob)).andExpect(status().isForbidden());
        mvc.perform(delete("/notes/{id}", id).header("X-User-Id", alice)).andExpect(status().isNoContent());
        mvc.perform(get("/notes/{id}", id).header("X-User-Id", alice)).andExpect(status().isNotFound());
    }

    @Test
    void cannotShareWithTeamYouDoNotBelongTo() throws Exception {
        create(carol, "Nope", "x", team, "[]").andExpect(status().isForbidden());
    }

    @Test
    void listSupportsSearchTagFilterAndPagination() throws Exception {
        createNote(alice, "Sprint planning", "estimate stories", team, "[\"work\"]");
        createNote(alice, "Groceries", "milk and eggs", null, "[\"home\"]");
        createNote(bob, "Retro", "what went well", team, "[\"work\"]");

        mvc.perform(get("/notes").header("X-User-Id", bob)) // bob sees: Retro + Sprint planning
                .andExpect(jsonPath("$.total").value(2));
        mvc.perform(get("/notes").header("X-User-Id", alice).param("q", "MILK"))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].title").value("Groceries"));
        mvc.perform(get("/notes").header("X-User-Id", alice).param("tag", "work"))
                .andExpect(jsonPath("$.total").value(2));
        mvc.perform(get("/notes").header("X-User-Id", alice).param("size", "1").param("page", "1"))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.total").value(3));
    }

    @Test
    void validationAndAuthErrorsUseConsistentShape() throws Exception {
        create(alice, "  ", "x", null, "[]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.details", hasSize(1)));

        mvc.perform(get("/notes")).andExpect(status().isUnauthorized());
        mvc.perform(get("/notes").header("X-User-Id", 99999)).andExpect(status().isUnauthorized());
    }

    @Test
    void userWithNoTeamsListsOnlyOwnNotes() throws Exception {
        createNote(alice, "Team only", "x", team, "[]");
        createNote(carol, "Carol's", "x", null, "[]");

        mvc.perform(get("/notes").header("X-User-Id", carol))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].title").value("Carol's"));
    }

    @Test
    void meReturnsTheCaller() throws Exception {
        mvc.perform(get("/users/me").header("X-User-Id", bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bob))
                .andExpect(jsonPath("$.name").value("Bob"));
        mvc.perform(get("/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void teamIsReadableByMembersOnly() throws Exception {
        mvc.perform(get("/teams/{t}", team).header("X-User-Id", bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Platform"))
                .andExpect(jsonPath("$.memberIds", hasSize(2)));
        mvc.perform(get("/teams/{t}", team).header("X-User-Id", carol)).andExpect(status().isNotFound());
        mvc.perform(get("/teams/{t}", 99999).header("X-User-Id", alice)).andExpect(status().isNotFound());
    }

    @Test
    void listTeamsReturnsOnlyCallersTeams() throws Exception {
        mvc.perform(post("/teams").header("X-User-Id", carol)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Carol's team\"}"))
                .andExpect(status().isCreated());

        mvc.perform(get("/teams").header("X-User-Id", alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Platform"));
        mvc.perform(get("/teams").header("X-User-Id", carol))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Carol's team"));
    }

    @Test
    void malformedRequestsReturn400() throws Exception {
        mvc.perform(post("/notes").header("X-User-Id", alice)
                        .contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Malformed request"));
        mvc.perform(get("/notes").header("X-User-Id", alice).param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Malformed request"));
    }

    @Test
    void creatingNoteForUnknownTeamReturns404() throws Exception {
        create(alice, "t", "x", 99999L, "[]")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Team not found"));
    }

    @Test
    void tagsAreOptionalOnCreate() throws Exception {
        mvc.perform(post("/notes").header("X-User-Id", alice)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"No tags\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tags", hasSize(0)));
    }

    @Test
    void oversizedInputIsRejectedAsValidationError() throws Exception {
        String longText = "x".repeat(300);

        create(alice, "t", "x", null, "[\"" + longText + "\"]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details", hasSize(1)));
        mvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + longText + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void frameworkErrorsUseConsistentShape() throws Exception {
        mvc.perform(patch("/notes/1").header("X-User-Id", alice))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.details", hasSize(0)));
        mvc.perform(post("/notes").header("X-User-Id", alice).contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415));
        mvc.perform(get("/no-such-path").header("X-User-Id", alice))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // ---- helpers ----

    private long createUser(String name) throws Exception {
        String body = mvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private long createTeam(long caller, String name) throws Exception {
        String body = mvc.perform(post("/teams").header("X-User-Id", caller)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private ResultActions create(long caller, String title, String content, Long teamId, String tagsJson)
            throws Exception {
        String json = "{\"title\":\"" + title + "\",\"content\":\"" + content + "\","
                + "\"teamId\":" + teamId + ",\"tags\":" + tagsJson + "}";
        return mvc.perform(post("/notes").header("X-User-Id", caller)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private long createNote(long caller, String title, String content, Long teamId, String tagsJson)
            throws Exception {
        String body = create(caller, title, content, teamId, tagsJson)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }
}
