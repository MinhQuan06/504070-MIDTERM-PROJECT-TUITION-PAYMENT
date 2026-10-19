package vn.edu.tdtu.ibanking.paymentservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.tdtu.ibanking.paymentservice.entity.Transaction;

import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select t from Transaction t where t.transactionId = :id")
    java.util.Optional<Transaction> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);


    // Phục vụ truy vấn lịch sử giao dịch theo index (payer_user_id, created_at DESC)
    List<Transaction> findByPayerUserIdOrderByCreatedAtDesc(Long payerUserId);
}