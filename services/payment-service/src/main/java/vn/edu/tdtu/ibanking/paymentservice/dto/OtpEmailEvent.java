package vn.edu.tdtu.ibanking.paymentservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtpEmailEvent {
    private String eventId;
    private String type;
    private String transactionId;
    private String email;
    private String fullName;
    private String otp;
    private Instant expiresAt;
}
