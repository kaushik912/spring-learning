package com.example.emailsimple;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Demo/support endpoints. NO AUTH here; protect these in a real app. */
@RestController
public class AdminController {

    private final EmailService service;

    public AdminController(EmailService service) {
        this.service = service;
    }

    @GetMapping("/admin/emails/report")
    public Map<String, Object> report(@RequestParam(required = false) String runId) {
        return service.report(runId != null ? runId : EmailJobs.today());
    }

    /** Customer says "I never got it": re-queue that user; the sweeper sends it on its next tick. */
    @PostMapping("/admin/emails/{runId}/{userId}/resend")
    public ResponseEntity<String> resend(@PathVariable String runId, @PathVariable int userId,
                                         @RequestParam(defaultValue = "support") String by) {
        return service.resend(runId, userId, by)
                ? ResponseEntity.accepted().body("queued, sweeper will send it")
                : ResponseEntity.status(409).body("not found, or still PENDING (will be sent anyway)");
    }
}
