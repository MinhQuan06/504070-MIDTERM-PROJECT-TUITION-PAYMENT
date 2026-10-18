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
public class OtpEmailEvent {
    private String eventType;       // "OTP_GENERATED"
    private Long transactionId;
    private String recipientEmail;
    private String payerFullName;
    private String studentId;
    private Long amount;
    private String otpCode;
    private int expiryMinutes;
    private LocalDateTime timestamp;
}