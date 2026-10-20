package vn.edu.tdtu.ibanking.paymentservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name="app.saga.recovery-enabled", havingValue="true", matchIfMissing=true)
public class PaymentRecoveryScheduler {
    private final PaymentSagaWorker worker;
    @Scheduled(fixedDelayString="${app.saga.poll-delay-ms:1000}")
    public void recover() {
        for (int i = 0; i < 10; i++) {
            try { if (!worker.processNext()) return; }
            catch (Exception ex) { log.warn("Payment recovery deferred after local failure"); return; }
        }
    }
}
