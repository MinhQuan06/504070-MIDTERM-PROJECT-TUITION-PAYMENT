package vn.edu.tdtu.ibanking.paymentservice.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class OutboxStore {
    private final JdbcTemplate jdbc;
    private final MessageConverter converter;
    private final OutboxPayloadCipher cipher;

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(String eventId, String type, String transactionId, String exchange,
                        String routingKey, Object event, Instant expiresAt) {
        if ("OtpRequested".equals(type)) {
            jdbc.update("UPDATE outbox_events SET status='EXPIRED', payload_ciphertext=NULL, last_error_code='OTP_SUPERSEDED' " +
                    "WHERE transaction_id=? AND event_type='OtpRequested' AND status='PENDING'", Long.parseLong(transactionId));
        }
        byte[] body = converter.toMessage(event, new MessageProperties()).getBody();
        jdbc.update("INSERT INTO outbox_events (event_id, transaction_id, event_type, exchange_name, routing_key, payload_ciphertext, expires_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?)",
                eventId, Long.parseLong(transactionId), type, exchange, routingKey, cipher.encrypt(body),
                expiresAt == null ? null : LocalDateTime.ofInstant(expiresAt, ZoneOffset.UTC));
    }
}
