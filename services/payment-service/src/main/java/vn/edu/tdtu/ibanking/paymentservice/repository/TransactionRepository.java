package vn.edu.tdtu.ibanking.paymentservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.tdtu.ibanking.paymentservice.entity.Transaction;

import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    // Phục vụ truy vấn lịch sử giao dịch theo index (payer_user_id, created_at DESC)
    List<Transaction> findByPayerUserIdOrderByCreatedAtDesc(Long payerUserId);
}