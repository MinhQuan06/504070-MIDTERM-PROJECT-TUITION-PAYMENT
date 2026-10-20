package vn.edu.tdtu.ibanking.paymentservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transaction_id")
    private Long transactionId;

    @Column(name = "payer_user_id", nullable = false)
    private Long payerUserId;

    @Column(name = "tuition_id", nullable = false)
    private Long tuitionId;

    @Column(name = "student_id", nullable = false, length = 10)
    private String studentId;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private TransactionStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "tuition_version")
    private Integer tuitionVersion;

    @Column(name = "payer_email", length = 254)
    private String payerEmail;

    @Column(name = "payer_full_name", length = 100)
    private String payerFullName;

    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    @Column(name = "saga_attempts", nullable = false)
    @Builder.Default
    private int sagaAttempts = 0;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}