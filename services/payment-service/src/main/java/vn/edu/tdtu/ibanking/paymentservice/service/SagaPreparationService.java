package vn.edu.tdtu.ibanking.paymentservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.tdtu.ibanking.paymentservice.client.UserServiceClient;
import vn.edu.tdtu.ibanking.paymentservice.entity.*;
import vn.edu.tdtu.ibanking.paymentservice.repository.TransactionRepository;
import vn.edu.tdtu.ibanking.paymentservice.exception.*;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SagaPreparationService {
    private final TransactionRepository repository;
    private final OtpService otp;
    private final AccountService accounts;
    private final UserServiceClient users;

    /** OTP use, debit and durable recovery state commit together, or all roll back. */
    @Transactional
    public Transaction prepare(Long id, String code) {
        Transaction tx = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
        if (tx.getStatus() == TransactionStatus.SUCCESS || tx.getStatus() == TransactionStatus.PROCESSING) return tx;
        if (tx.getStatus() != TransactionStatus.PENDING) throw new BusinessException("Transaction already failed");
        if (tx.getPayerEmail() == null) {
            var user = users.getUserById(tx.getPayerUserId());
            tx.setPayerEmail(user.getEmail()); tx.setPayerFullName(user.getFullName());
        }
        otp.verifyAndConsumeOtp(id, code);
        accounts.deductBalance(tx.getPayerUserId(), tx.getAmount());
        tx.setStatus(TransactionStatus.PROCESSING);
        tx.setNextAttemptAt(LocalDateTime.now(java.time.ZoneOffset.UTC));
        return repository.save(tx);
    }
}
