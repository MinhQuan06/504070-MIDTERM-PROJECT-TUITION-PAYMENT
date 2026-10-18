package vn.edu.tdtu.ibanking.paymentservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.edu.tdtu.ibanking.paymentservice.dto.OtpEmailEvent;
import vn.edu.tdtu.ibanking.paymentservice.dto.PaymentSuccessEmailEvent;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationProducer {

    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbitmq.exchange}")
    private String exchangeName;

    @Value("${app.rabbitmq.otp-routing-key}")
    private String otpRoutingKey;

    @Value("${app.rabbitmq.payment-success-routing-key}")
    private String paymentSuccessRoutingKey;

    /**
     * Bắn sự kiện yêu cầu Notification Service gửi email mã OTP
     */
    public void sendOtpEmailEvent(OtpEmailEvent event) {
        try {
            rabbitTemplate.convertAndSend(exchangeName, otpRoutingKey, event);
            log.info("[RABBITMQ] Đã đẩy sự kiện OTP_GENERATED cho txId={} tới email={}",
                    event.getTransactionId(), event.getRecipientEmail());
        } catch (Exception e) {
            log.error("[RABBITMQ ERROR] Không thể đẩy sự kiện OTP_GENERATED cho txId={}: {}",
                    event.getTransactionId(), e.getMessage());
        }
    }

    /**
     * Bắn sự kiện yêu cầu Notification Service gửi email biên lai xác nhận thanh toán thành công
     */
    public void sendPaymentSuccessEmailEvent(PaymentSuccessEmailEvent event) {
        try {
            rabbitTemplate.convertAndSend(exchangeName, paymentSuccessRoutingKey, event);
            log.info("[RABBITMQ] Đã đẩy sự kiện PAYMENT_SUCCESS cho txId={} tới email={}",
                    event.getTransactionId(), event.getRecipientEmail());
        } catch (Exception e) {
            log.error("[RABBITMQ ERROR] Không thể đẩy sự kiện PAYMENT_SUCCESS cho txId={}: {}",
                    event.getTransactionId(), e.getMessage());
        }
    }
}