package vn.edu.tdtu.ibanking.paymentservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.tdtu.ibanking.paymentservice.client.StudentServiceClient;
import vn.edu.tdtu.ibanking.paymentservice.dto.UserDto;
import vn.edu.tdtu.ibanking.paymentservice.entity.*;
import vn.edu.tdtu.ibanking.paymentservice.repository.TransactionRepository;
import vn.edu.tdtu.ibanking.paymentservice.exception.ResourceNotFoundException;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentSagaWorker {
    private final TransactionRepository repository;
    private final StudentServiceClient students;
    private final AccountService accounts;
    private final PaymentCompletionService completion;

    @Transactional
    public Transaction process(Long id) {
        var tx = repository.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
        return apply(tx);
    }

    @Transactional
    public boolean processNext() {
        var next = repository.findNextRecoveryForUpdate();
        if (next.isEmpty()) return false;
        apply(next.get());
        return true;
    }

    private Transaction apply(Transaction tx) {
        if (tx.getStatus() != TransactionStatus.PROCESSING
                || (tx.getNextAttemptAt() != null && tx.getNextAttemptAt().isAfter(LocalDateTime.now(java.time.ZoneOffset.UTC)))) return tx;
        // Serialize HTTP and scheduler retries with this transaction row lock. Remote call is bounded.
        var result = students.markTuitionAsPaid(tx.getTuitionId(), tx.getTransactionId(), tx.getStudentId(), tx.getAmount(), tx.getTuitionVersion());
        if (result == StudentServiceClient.PaymentResult.APPLIED) {
            return completion.complete(tx.getTransactionId(), UserDto.builder().userId(tx.getPayerUserId())
                    .email(tx.getPayerEmail()).fullName(tx.getPayerFullName()).build());
        }
        if (result == StudentServiceClient.PaymentResult.REJECTED) {
            accounts.refundBalance(tx.getPayerUserId(), tx.getAmount());
            tx.setStatus(TransactionStatus.FAILED);
            tx.setCompletedAt(LocalDateTime.now());
            tx.setNextAttemptAt(null);
        } else {
            tx.setSagaAttempts(tx.getSagaAttempts() + 1);
            long delay = Math.min(60, 1L << Math.min(tx.getSagaAttempts(), 6));
            tx.setNextAttemptAt(LocalDateTime.now(java.time.ZoneOffset.UTC).plusSeconds(delay));
            log.warn("Payment outcome uncertain; scheduled retry for transactionId={}, attempt={}", tx.getTransactionId(), tx.getSagaAttempts());
        }
        return repository.save(tx);
    }
}
