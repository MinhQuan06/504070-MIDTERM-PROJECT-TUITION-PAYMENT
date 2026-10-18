package vn.edu.tdtu.ibanking.paymentservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentSuccessEmailEvent {
    private String eventType;       // "PAYMENT_SUCCESS"
    private Long transactionId;
    private String recipientEmail;
    private String payerFullName;
    private String studentId;
    private Long tuitionId;
    private Long amount;
    private Long remainingBalance;
    private LocalDateTime completedAt;
}