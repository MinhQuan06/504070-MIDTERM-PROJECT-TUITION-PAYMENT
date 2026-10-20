package vn.edu.tdtu.ibanking.paymentservice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.tdtu.ibanking.paymentservice.dto.*;
import vn.edu.tdtu.ibanking.paymentservice.entity.Transaction;
import vn.edu.tdtu.ibanking.paymentservice.entity.TransactionStatus;
import vn.edu.tdtu.ibanking.paymentservice.outbox.*;
import vn.edu.tdtu.ibanking.paymentservice.repository.TransactionRepository;
import vn.edu.tdtu.ibanking.paymentservice.service.NotificationProducer;
import vn.edu.tdtu.ibanking.paymentservice.service.PaymentCompletionService;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="OUTBOX_TEST_DATABASE", matches="true")
@SpringBootTest(properties={"app.saga.recovery-enabled=false", "app.outbox.enabled=false", "app.outbox.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="})
class OutboxIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @Autowired TransactionRepository transactions;
    @Autowired NotificationProducer notifications;
    @Autowired PaymentCompletionService completion;
    @Autowired OutboxDelivery delivery;
    @Autowired OutboxPayloadCipher cipher;
    @MockitoBean RabbitOutboxSender sender;
    private TransactionTemplate template;
    @BeforeEach void setup() {
        assertEquals("payment_outbox_test", jdbc.queryForObject("SELECT DATABASE()", String.class), "Use only the isolated Outbox test DB");
        template = new TransactionTemplate(manager);
    }
    private Transaction transaction() {
        return transactions.saveAndFlush(Transaction.builder().payerUserId(1L).tuitionId(101L)
                .studentId("52300001").amount(1000L).status(TransactionStatus.PENDING).build());
    }
    private OtpEmailEvent event(Long id, Instant expiry) {
        return OtpEmailEvent.builder().eventId("test-"+UUID.randomUUID()).type("OtpRequested")
                .transactionId(id.toString()).email("test@example.com").fullName("Test")
                .otp("123456").expiresAt(expiry).build();
    }
    @org.junit.jupiter.api.AfterEach void cleanupTestRows() {
        if ("payment_outbox_test".equals(jdbc.queryForObject("SELECT DATABASE()", String.class))) clear();
    }
    private void clear() { jdbc.update("DELETE FROM outbox_events"); }
    @Test void businessRollbackAlsoRollsBackOutbox() {
        clear();
        String[] id = new String[1]; Long[] tx = new Long[1];
        assertThrows(IllegalStateException.class, () -> template.executeWithoutResult(status -> {
            tx[0] = transaction().getTransactionId();
            var event = event(tx[0], Instant.now().plusSeconds(300)); id[0] = event.getEventId();
            notifications.sendOtpEmailEvent(event);
            throw new IllegalStateException("rollback test");
        }));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events WHERE event_id=?", Integer.class, id[0]));
        assertFalse(transactions.existsById(tx[0]));
    }
    @Test void enqueueRequiresAnExistingTransaction() {
        assertThrows(org.springframework.transaction.IllegalTransactionStateException.class,
                () -> notifications.sendOtpEmailEvent(event(1L, Instant.now().plusSeconds(300))));
    }
    @Test void failedPublishRetainsCiphertextAndRetryKeepsSameEventId() {
        clear(); var tx = transaction(); var event = event(tx.getTransactionId(), Instant.now().plusSeconds(300));
        template.executeWithoutResult(s -> notifications.sendOtpEmailEvent(event));
        String encrypted=jdbc.queryForObject("SELECT payload_ciphertext FROM outbox_events WHERE event_id=?",String.class,event.getEventId());
        assertFalse(encrypted.contains("123456"));
        assertTrue(new String(cipher.decrypt(encrypted), java.nio.charset.StandardCharsets.UTF_8).contains("OtpRequested"));
        doThrow(new RabbitOutboxSender.PublishFailure("BROKER_UNAVAILABLE_OR_TIMEOUT")).when(sender)
                .send(eq(event.getEventId()),anyString(),anyString(),any(byte[].class));
        assertTrue(delivery.processNext());
        assertEquals("PENDING",jdbc.queryForObject("SELECT status FROM outbox_events WHERE event_id=?",String.class,event.getEventId()));
        assertNotNull(jdbc.queryForObject("SELECT payload_ciphertext FROM outbox_events WHERE event_id=?",String.class,event.getEventId()));
        jdbc.update("UPDATE outbox_events SET available_at=UTC_TIMESTAMP(6) WHERE event_id=?",event.getEventId());
        doNothing().when(sender).send(anyString(),anyString(),anyString(),any(byte[].class));
        assertTrue(delivery.processNext());
        assertEquals("PUBLISHED",jdbc.queryForObject("SELECT status FROM outbox_events WHERE event_id=?",String.class,event.getEventId()));
        assertNull(jdbc.queryForObject("SELECT payload_ciphertext FROM outbox_events WHERE event_id=?",String.class,event.getEventId()));
        verify(sender,times(2)).send(eq(event.getEventId()),anyString(),anyString(),any(byte[].class));
    }
    @Test void expiredAndSupersededOtpAreScrubbedWithoutPublishing() {
        clear(); var tx=transaction(); var expired=event(tx.getTransactionId(),Instant.now().minusSeconds(1));
        template.executeWithoutResult(s->notifications.sendOtpEmailEvent(expired));
        delivery.processNext();
        assertEquals("EXPIRED",jdbc.queryForObject("SELECT status FROM outbox_events WHERE event_id=?",String.class,expired.getEventId()));
        assertNull(jdbc.queryForObject("SELECT payload_ciphertext FROM outbox_events WHERE event_id=?",String.class,expired.getEventId()));
        verifyNoInteractions(sender);
        var first=event(tx.getTransactionId(),Instant.now().plusSeconds(300));
        var second=event(tx.getTransactionId(),Instant.now().plusSeconds(300));
        template.executeWithoutResult(s->{notifications.sendOtpEmailEvent(first);notifications.sendOtpEmailEvent(second);});
        assertEquals("OTP_SUPERSEDED",jdbc.queryForObject("SELECT last_error_code FROM outbox_events WHERE event_id=?",String.class,first.getEventId()));
        assertNull(jdbc.queryForObject("SELECT payload_ciphertext FROM outbox_events WHERE event_id=?",String.class,first.getEventId()));
    }
    @Test void anotherPublisherSkipsALockedEvent() {
        clear(); var tx=transaction(); var event=event(tx.getTransactionId(),Instant.now().plusSeconds(300));
        template.executeWithoutResult(s->notifications.sendOtpEmailEvent(event));
        template.executeWithoutResult(s->{
            jdbc.queryForObject("SELECT event_id FROM outbox_events WHERE event_id=? FOR UPDATE",String.class,event.getEventId());
            var result=java.util.concurrent.CompletableFuture.supplyAsync(()->delivery.processNext());
            try { assertFalse(result.get(3,java.util.concurrent.TimeUnit.SECONDS)); }
            catch(Exception error) { throw new AssertionError("Publisher must skip row locked by another transaction",error); }
        });
        verifyNoInteractions(sender);
    }
    @Test void successAndReceiptOutboxCommitOrRollbackTogether() {
        clear(); var tx=transaction();
        tx.setStatus(TransactionStatus.PROCESSING); transactions.saveAndFlush(tx);
        var payer=UserDto.builder().userId(1L).fullName("Test").email("test@example.com").build();
        completion.complete(tx.getTransactionId(),payer);
        assertEquals(TransactionStatus.SUCCESS,transactions.findById(tx.getTransactionId()).orElseThrow().getStatus());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events WHERE event_id=?",Integer.class,"payment-succeeded-"+tx.getTransactionId()));
        var conflict=transaction();
        conflict.setStatus(TransactionStatus.PROCESSING); transactions.saveAndFlush(conflict);
        template.executeWithoutResult(s -> notifications.sendPaymentSuccessEmailEvent(PaymentSuccessEmailEvent.builder()
                .eventId("payment-succeeded-"+conflict.getTransactionId()).type("PaymentSucceeded")
                .transactionId(conflict.getTransactionId().toString()).email("test@example.com").fullName("Test")
                .studentId("52300001").amount(1000L).completedAt(Instant.now()).build()));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->completion.complete(conflict.getTransactionId(),payer));
        assertEquals(TransactionStatus.PROCESSING,transactions.findById(conflict.getTransactionId()).orElseThrow().getStatus());
    }
}
