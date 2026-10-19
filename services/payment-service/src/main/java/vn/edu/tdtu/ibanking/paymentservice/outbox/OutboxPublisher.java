package vn.edu.tdtu.ibanking.paymentservice.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name="app.outbox.enabled", havingValue="true", matchIfMissing=true)
public class OutboxPublisher {
    private final OutboxDelivery delivery;
    @Scheduled(fixedDelayString="${app.outbox.poll-delay-ms:1000}")
    public void publish() {
        try { for (int i=0; i<10 && delivery.processNext(); i++) { /* bounded batch */ } }
        catch (RuntimeException error) { log.warn("Outbox scan failed; will retry next poll"); }
    }
}
