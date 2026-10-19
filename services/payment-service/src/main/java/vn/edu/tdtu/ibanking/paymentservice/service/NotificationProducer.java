package vn.edu.tdtu.ibanking.paymentservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.tdtu.ibanking.paymentservice.dto.OtpEmailEvent;
import vn.edu.tdtu.ibanking.paymentservice.dto.PaymentSuccessEmailEvent;
import vn.edu.tdtu.ibanking.paymentservice.outbox.OutboxStore;

@Service
@RequiredArgsConstructor
public class NotificationProducer {
    private final OutboxStore outbox;
    @Value("${app.rabbitmq.exchange}") private String exchange;
    @Value("${app.rabbitmq.otp-routing-key}") private String otpKey;
    @Value("${app.rabbitmq.payment-success-routing-key}") private String paidKey;

    @Transactional(propagation=Propagation.MANDATORY)
    public void sendOtpEmailEvent(OtpEmailEvent event) {
        outbox.enqueue(event.getEventId(), event.getType(), event.getTransactionId(), exchange, otpKey, event, event.getExpiresAt());
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void sendPaymentSuccessEmailEvent(PaymentSuccessEmailEvent event) {
        outbox.enqueue(event.getEventId(), event.getType(), event.getTransactionId(), exchange, paidKey, event, null);
    }
}
