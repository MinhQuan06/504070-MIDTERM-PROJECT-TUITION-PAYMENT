package vn.edu.tdtu.ibanking.paymentservice.outbox;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.concurrent.TimeUnit;

@Component
public class RabbitOutboxSender {
    private final RabbitTemplate template;
    private final long confirmTimeout;
    public RabbitOutboxSender(RabbitTemplate template, @Value("${app.outbox.confirm-timeout-ms:5000}") long timeout) {
        this.template = template; this.confirmTimeout = timeout;
    }
    public void send(String eventId, String exchange, String routingKey, byte[] body) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setContentEncoding("UTF-8");
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        properties.setMessageId(eventId);
        CorrelationData correlation = new CorrelationData();
        try {
            template.send(exchange, routingKey, new Message(body, properties), correlation);
            CorrelationData.Confirm confirm = correlation.getFuture().get(confirmTimeout, TimeUnit.MILLISECONDS);
            if (correlation.getReturned() != null) throw new PublishFailure("UNROUTABLE");
            if (!confirm.ack()) throw new PublishFailure("BROKER_NACK");
        } catch (PublishFailure error) { throw error; }
        catch (InterruptedException error) {
            Thread.currentThread().interrupt(); throw new PublishFailure("PUBLISH_INTERRUPTED");
        } catch (Exception error) { throw new PublishFailure("BROKER_UNAVAILABLE_OR_TIMEOUT"); }
    }
    public static class PublishFailure extends RuntimeException {
        public PublishFailure(String code) { super(code); }
    }
}
