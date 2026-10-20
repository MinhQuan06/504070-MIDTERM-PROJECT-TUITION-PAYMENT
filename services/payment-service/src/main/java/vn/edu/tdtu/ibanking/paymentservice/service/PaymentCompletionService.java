package vn.edu.tdtu.ibanking.paymentservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.tdtu.ibanking.paymentservice.dto.PaymentSuccessEmailEvent;
import vn.edu.tdtu.ibanking.paymentservice.dto.UserDto;
import vn.edu.tdtu.ibanking.paymentservice.entity.Transaction;
import vn.edu.tdtu.ibanking.paymentservice.entity.TransactionStatus;
import vn.edu.tdtu.ibanking.paymentservice.exception.BusinessException;
import vn.edu.tdtu.ibanking.paymentservice.exception.ResourceNotFoundException;
import vn.edu.tdtu.ibanking.paymentservice.repository.TransactionRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class PaymentCompletionService {
    private final TransactionRepository repository;
    private final NotificationProducer notifications;
    @Transactional
    public Transaction complete(Long transactionId, UserDto payer) {
        Transaction tx = repository.findByIdForUpdate(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
        if (tx.getStatus() == TransactionStatus.SUCCESS) return tx;
        if (tx.getStatus() != TransactionStatus.PROCESSING) throw new BusinessException("Transaction has not entered processing");
        tx.setStatus(TransactionStatus.SUCCESS);
        tx.setNextAttemptAt(null);
        tx.setCompletedAt(LocalDateTime.now());
        tx = repository.save(tx);
        notifications.sendPaymentSuccessEmailEvent(PaymentSuccessEmailEvent.builder()
                .eventId("payment-succeeded-" + tx.getTransactionId()).type("PaymentSucceeded")
                .transactionId(tx.getTransactionId().toString()).email(payer.getEmail()).fullName(payer.getFullName())
                .studentId(tx.getStudentId()).amount(tx.getAmount())
                .completedAt(tx.getCompletedAt().atZone(ZoneId.systemDefault()).toInstant()).build());
        return tx;
    }
}
