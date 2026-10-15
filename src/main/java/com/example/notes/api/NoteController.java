package com.example.notes.api;

import com.example.notes.service.NoteService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/notes")
public class NoteController {

    static final String USER_HEADER = "X-User-Id";

    private final NoteService service;

    public NoteController(NoteService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NoteResponse create(@RequestHeader(USER_HEADER) Long userId, @Valid @RequestBody NoteRequest req) {
        return service.create(userId, req);
    }

    @GetMapping
    public PageResult<NoteResponse> list(@RequestHeader(USER_HEADER) Long userId,
                                         @RequestParam(required = false) String q,
                                         @RequestParam(required = false) String tag,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return service.list(userId, q, tag, page, size);
    }

    @GetMapping("/{id}")
    public NoteResponse get(@RequestHeader(USER_HEADER) Long userId, @PathVariable Long id) {
        return service.get(userId, id);
    }

    @PutMapping("/{id}")
    public NoteResponse update(@RequestHeader(USER_HEADER) Long userId,
                               @PathVariable Long id,
                               @Valid @RequestBody NoteRequest req) {
        return service.update(userId, id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestHeader(USER_HEADER) Long userId, @PathVariable Long id) {
        service.delete(userId, id);
    }
}
