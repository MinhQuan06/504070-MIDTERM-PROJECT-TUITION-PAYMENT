package vn.edu.tdtu.ibanking.paymentservice.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
public class InternalNotificationController {
    private final JdbcTemplate jdbc;
    @Value("${app.external-services.user-internal-api-key:}") private String key;

    @GetMapping("/internal/notifications/otp-current/{eventId}")
    public Map<String, Boolean> current(@PathVariable String eventId,
            @RequestHeader(value="X-Internal-API-Key", defaultValue="") String provided) {
        if (key.isBlank() || provided.isBlank() || !MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid internal credentials");
        var count = jdbc.queryForObject("SELECT COUNT(*) FROM otps o JOIN transactions t ON t.transaction_id=o.transaction_id "
                + "WHERE o.event_id=? AND o.used_at IS NULL AND o.expires_at>? "
                + "AND t.status='PENDING' AND t.otp_failed_attempts<5", Integer.class, eventId, java.time.LocalDateTime.now());
        return Map.of("current", count != null && count > 0);
    }
}
