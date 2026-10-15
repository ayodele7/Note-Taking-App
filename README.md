# Notes Service
A small REST backend for a note-taking service shared across several small teams.
Users keep private notes or share notes with a team; teammates can read and edit shared notes.

**Stack:** 
Java 21, Spring Boot 3.3, Spring Data JPA, H2 (file-backed), Maven.

## Run it
Requires **Java 21**. Maven is not needed; the wrapper downloads it.

```bash
./mvnw spring-boot:run     # or: mvn spring-boot:run
./mvnw test
```

The service starts on `http://localhost:8080`. Data is stored in `./data/notes.mv.db`, so it survives restarts.
Delete the `data/` folder to start from an empty database.

Tests live in `src/test/.../NoteApiIntegrationTest.java`. They drive the real HTTP layer and database
(in-memory H2), and cover access rules, search, tags, pagination, validation and the error format.

- **API docs (Swagger UI):** http://localhost:8080/swagger-ui.html
- **Database console (optional):** start with
  `./mvnw spring-boot:run -Dspring-boot.run.arguments=--spring.h2.console.enabled=true`,
  open http://localhost:8080/h2-console and connect with JDBC URL `jdbc:h2:file:./data/notes;AUTO_SERVER=TRUE`,
  user `sa`, empty password.

## Scope

**In:** users, teams and membership; notes with create/read/update/delete; private vs. team-shared notes;
tags; text search; pagination; input validation; a consistent error format; integration tests.

**Out (deliberately):** real authentication, a UI, real-time collaboration, attachments, note history.

## Identity

There is no login. The caller's user id is sent in the `X-User-Id` header, and every endpoint except
`POST /users` requires it. A missing header or an unknown id returns `401`.

## Access rules

- A note is visible to its **owner** and, if it is shared with a team, to **every member of that team**.
- The owner and team members can **edit** a note; only the **owner** can **delete** it.
- A note's team is chosen when it is created and can't be changed afterwards.
- You can only share a note with a team you belong to, and only team members can add members.
- Anything you aren't allowed to see (a note or a team) returns `404`, exactly as if it didn't exist,
  so ids can't be probed. `403` is used only when you *can* see a note but may not delete it.

## API

| Method | Path | Description |
| --- | --- | --- |
| POST | `/users` | Create a user: `{"name": "Alice"}` |
| GET | `/users/me` | The calling user |
| POST | `/teams` | Create a team: `{"name": "Platform"}`. The caller becomes its first member |
| GET | `/teams` | Teams the caller belongs to |
| GET | `/teams/{teamId}` | A team and its member ids (members only) |
| PUT | `/teams/{teamId}/members/{userId}` | Add a member (members only; adding twice is harmless) |
| POST | `/notes` | Create a note: `{"title", "content"?, "teamId"?, "tags"?}`. No `teamId` means private |
| GET | `/notes` | Notes the caller can see, newest first. Query params: `q`, `tag`, `page`, `size` |
| GET | `/notes/{id}` | One note |
| PUT | `/notes/{id}` | Replace title, content and tags (`teamId` is ignored) |
| DELETE | `/notes/{id}` | Delete a note (owner only) |

**Listing:** `q` is a case-insensitive "contains" search over title and content. `tag` filters to one exact tag.
Pages start at `0`; `size` defaults to 20 and is clamped to 1–100. Responses look like
`{"items": [...], "page": 0, "size": 20, "total": 57}`.

**Tags:** trimmed and lowercased on save, so `Work` and `work` are the same tag. Blank tags are dropped.
`PUT` replaces the whole set; leaving `tags` out clears them.

**Limits:** title 1–200 characters, content up to 10,000, up to 20 tags of up to 50 characters each,
user and team names up to 100.

### Quick try

```bash
H='Content-Type: application/json'

curl -s -XPOST localhost:8080/users -H "$H" -d '{"name":"Alice"}'                      # id 1
curl -s -XPOST localhost:8080/users -H "$H" -d '{"name":"Bob"}'                        # id 2
curl -s -XPOST localhost:8080/teams -H 'X-User-Id: 1' -H "$H" -d '{"name":"Platform"}' # team 1
curl -s -XPUT  localhost:8080/teams/1/members/2 -H 'X-User-Id: 1'

curl -s -XPOST localhost:8080/notes -H 'X-User-Id: 1' -H "$H" \
  -d '{"title":"Diary","content":"private","tags":["home"]}'
curl -s -XPOST localhost:8080/notes -H 'X-User-Id: 1' -H "$H" \
  -d '{"title":"Standup","content":"ship it","teamId":1,"tags":["work"]}'

curl -s 'localhost:8080/notes?tag=work&q=ship' -H 'X-User-Id: 2'   # Bob sees only the team note
```

(Ids assume an empty database.)

## Design choices

1. **Who can see a note.** A note is either private or shared with one team, and everyone in that team
can read and edit it. This keeps the rule simple: you see a note if you own it or you're in its team.
If you can't see a note or team, you get a `404`, the same as if it didn't exist, so nobody can guess
which ids exist. The trade-off is that a note can't be shared with two teams or with one person; a
separate sharing table would allow that later.

2. **A simple Spring Boot CRUD service.** I kept to a standard layout (controllers → services →
repositories) so the code is easy to follow, and added Swagger so every endpoint can be seen and tried
in the browser. There's no login, so each request sends the caller's id in the `X-User-Id` header to
tell the service who is asking. Anyone could pretend to be another user, which is fine for a demo but
not for production. Real login could replace the header later without changing the business logic.

3. **Filtering happens in the database.** When listing notes, the database does the access check,
search, tag filter and paging in one query. Loading everything and filtering in Java would give wrong
page totals and get slower as notes grow. The cost is one small extra lookup for the caller's teams.

## What I'd change with more time

**Change**
- Replace the `X-User-Id` header with real authentication (OIDC/JWT). The service layer already takes a
  caller id, so only the controllers would change.
- Move from H2 to Postgres, and manage the schema with Flyway migrations instead of `ddl-auto=update`.
- Use database full-text search (e.g. Postgres `tsvector`) instead of `LIKE`, which can't use an index.
- Load tags together with each page of notes; today each note's tags are a separate query.

**Add**
- Sharing a note with several teams or individuals, and viewer/editor roles.
- Removing members, leaving a team, and moving a note to a different team.
- Protect against two teammates editing the same note at once (optimistic locking with `@Version`); today the last save silently wins.
- Note history and soft delete / trash.

**Stop doing**
- Reusing one request type for create and update: `PUT` silently ignores `teamId`. Separate types would make that explicit.
- Keeping all tests in one class; split them by area and add focused repository tests for the listing query.

## Errors

Every error has the same shape:

```json
{"status": 400, "error": "Validation failed", "details": ["title must not be blank"]}
```

| Status | When |
| --- | --- |
| 400 | Validation failed (`details` lists each field), malformed JSON, or a bad parameter type |
| 401 | `X-User-Id` missing or unknown |
| 403 | A team member tries to delete a note they don't own |
| 404 | The note or team doesn't exist, or you aren't allowed to see it |
| 405 / 415 | Wrong HTTP method / content type |
| 500 | Unexpected error (logged; no internals returned) |

## Data model

| Table | Holds |
| --- | --- |
| `users` | id, name |
| `teams` | id, name |
| `team_members` | which users belong to which teams |
| `notes` | id, title, content, owner, optional team, created/updated timestamps |
| `note_tags` | one row per tag on a note |

The schema is generated from the JPA entities in `model/`.
