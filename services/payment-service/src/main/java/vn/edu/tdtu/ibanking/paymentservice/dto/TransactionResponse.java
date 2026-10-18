package vn.edu.tdtu.ibanking.paymentservice.dto;

import lombok.Builder;
import lombok.Data;
import vn.edu.tdtu.ibanking.paymentservice.entity.TransactionStatus;

import java.time.LocalDateTime;

@Data
@Builder
public class TransactionResponse {
    private Long transactionId;
    private Long payerUserId;
    private String payerEmail;
    private Long tuitionId;
    private String studentId;
    private Long amount;
    private TransactionStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private String message;
}