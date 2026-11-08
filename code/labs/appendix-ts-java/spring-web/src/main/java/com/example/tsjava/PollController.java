package com.example.tsjava;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Express:  const router = express.Router();  app.use("/api", router)
@RestController
@RequestMapping("/api")
class PollController {

    record CreatePoll(@NotBlank @Size(max = 100) String title) {}   // zod: z.object({ title: z.string().min(1).max(100) })

    record PollView(long id, String title) {}

    private final Map<Long, PollView> store = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong();

    // router.get("/polls/:id", (req, res) => { req.params.id; req.query.verbose; req.get("X-Request-Id"); req.cookies.QV_VISITOR })
    @GetMapping("/polls/{id}")
    PollView get(@PathVariable long id,
                 @RequestParam(defaultValue = "false") boolean verbose,
                 @RequestHeader(name = "X-Request-Id", required = false) String requestId,
                 @CookieValue(name = "QV_VISITOR", required = false) String visitor) {
        PollView p = store.get(id);
        if (p == null) {
            throw new PollNotFoundException(id);
        }
        return p;
    }

    // router.post("/polls", (req, res) => res.status(201).location(...).json(created))
    @PostMapping("/polls")
    ResponseEntity<PollView> create(@Valid @RequestBody CreatePoll body) {
        long id = seq.incrementAndGet();
        PollView created = new PollView(id, body.title());
        store.put(id, created);
        return ResponseEntity.created(URI.create("/api/polls/" + id)).body(created);
    }

    // router.delete("/polls/:id", (req, res) => res.sendStatus(204))
    @DeleteMapping("/polls/{id}")
    ResponseEntity<Void> delete(@PathVariable long id) {
        store.remove(id);
        return ResponseEntity.noContent().build();
    }
}
