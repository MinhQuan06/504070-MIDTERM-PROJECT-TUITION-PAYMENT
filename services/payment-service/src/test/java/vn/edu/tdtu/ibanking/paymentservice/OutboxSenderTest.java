package vn.edu.tdtu.ibanking.paymentservice;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import vn.edu.tdtu.ibanking.paymentservice.outbox.OutboxPayloadCipher;
import vn.edu.tdtu.ibanking.paymentservice.outbox.RabbitOutboxSender;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;

class OutboxSenderTest {
    private static final String KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
    @Test void encryptionIsRandomizedAndAuthenticated() {
        var cipher = new OutboxPayloadCipher(KEY);
        byte[] body = "{\"otp\":\"123456\"}".getBytes(StandardCharsets.UTF_8);
        String first = cipher.encrypt(body), second = cipher.encrypt(body);
        assertNotEquals(first, second);
        assertArrayEquals(body, cipher.decrypt(first));
        var wrong = new OutboxPayloadCipher("AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=");
        assertThrows(IllegalStateException.class, () -> wrong.decrypt(first));
    }
    @Test void invalidKeyFailsFast() { assertThrows(IllegalArgumentException.class, () -> new OutboxPayloadCipher("")); }
    @Test void positiveConfirmUsesPersistentStableMessageId() {
        RabbitTemplate template = mock(RabbitTemplate.class);
        doAnswer(call -> {
            Message message = call.getArgument(2);
            assertEquals("event-1", message.getMessageProperties().getMessageId());
            assertEquals(MessageDeliveryMode.PERSISTENT, message.getMessageProperties().getDeliveryMode());
            ((CorrelationData)call.getArgument(3)).getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(template).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        new RabbitOutboxSender(template, 50).send("event-1", "events", "route", new byte[]{1});
    }
    @Test void returnedMessageIsNotTreatedAsPublishedEvenWithAck() {
        RabbitTemplate template = mock(RabbitTemplate.class);
        doAnswer(call -> {
            CorrelationData correlation = call.getArgument(3);
            correlation.setReturned(new ReturnedMessage(call.getArgument(2), 312, "NO_ROUTE", "events", "missing"));
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(template).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        var error = assertThrows(RabbitOutboxSender.PublishFailure.class,
                () -> new RabbitOutboxSender(template, 50).send("event-1", "events", "missing", new byte[]{1}));
        assertEquals("UNROUTABLE", error.getMessage());
    }
    @Test void nackAndTimeoutAreRetryable() {
        RabbitTemplate nack = mock(RabbitTemplate.class);
        doAnswer(call -> {
            ((CorrelationData)call.getArgument(3)).getFuture().complete(new CorrelationData.Confirm(false, "test")); return null;
        }).when(nack).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        assertEquals("BROKER_NACK", assertThrows(RabbitOutboxSender.PublishFailure.class,
                () -> new RabbitOutboxSender(nack, 50).send("e", "x", "r", new byte[]{1})).getMessage());
        assertEquals("BROKER_UNAVAILABLE_OR_TIMEOUT", assertThrows(RabbitOutboxSender.PublishFailure.class,
                () -> new RabbitOutboxSender(mock(RabbitTemplate.class), 10).send("e", "x", "r", new byte[]{1})).getMessage());
    }
}
