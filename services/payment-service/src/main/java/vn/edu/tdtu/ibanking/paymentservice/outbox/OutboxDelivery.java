package vn.edu.tdtu.ibanking.paymentservice.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxDelivery {
    private final JdbcTemplate jdbc;
    private final OutboxPayloadCipher cipher;
    private final RabbitOutboxSender sender;
    private record Pending(String id, String exchange, String key, String payload, int attempts, LocalDateTime expiresAt) {}
    private Pending map(ResultSet rs, int row) throws SQLException {
        return new Pending(rs.getString("event_id"), rs.getString("exchange_name"), rs.getString("routing_key"),
                rs.getString("payload_ciphertext"), rs.getInt("attempt_count"), rs.getObject("expires_at", LocalDateTime.class));
    }
    // Separate bean/proxy: one short DB transaction owns one event through broker confirmation.
    @Transactional
    public boolean processNext() {
        var rows = jdbc.query("SELECT event_id, exchange_name, routing_key, payload_ciphertext, attempt_count, expires_at " +
                "FROM outbox_events WHERE status='PENDING' AND available_at<=UTC_TIMESTAMP(6) " +
                "ORDER BY available_at, event_id LIMIT 1 FOR UPDATE SKIP LOCKED", this::map);
        if (rows.isEmpty()) return false;
        Pending event = rows.getFirst();
        if (event.expiresAt() != null && !event.expiresAt().isAfter(LocalDateTime.now(ZoneOffset.UTC))) {
            jdbc.update("UPDATE outbox_events SET status='EXPIRED', payload_ciphertext=NULL, last_error_code='OTP_EXPIRED' WHERE event_id=?", event.id());
            return true;
        }
        try {
            sender.send(event.id(), event.exchange(), event.key(), cipher.decrypt(event.payload()));
            jdbc.update("UPDATE outbox_events SET status='PUBLISHED', payload_ciphertext=NULL, attempt_count=attempt_count+1, " +
                    "published_at=UTC_TIMESTAMP(6), last_error_code=NULL WHERE event_id=?", event.id());
            log.info("Outbox published eventId={}", event.id());
        } catch (RuntimeException error) {
            String code = error instanceof RabbitOutboxSender.PublishFailure ? error.getMessage() : "OUTBOX_DELIVERY_ERROR";
            long delay = Math.min(60, 1L << Math.min(event.attempts(), 6));
            jdbc.update("UPDATE outbox_events SET attempt_count=attempt_count+1, last_error_code=?, available_at=? WHERE event_id=?",
                    code, LocalDateTime.now(ZoneOffset.UTC).plusSeconds(delay), event.id());
            log.warn("Outbox pending eventId={} code={}", event.id(), code);
        }
        return true;
    }
}
