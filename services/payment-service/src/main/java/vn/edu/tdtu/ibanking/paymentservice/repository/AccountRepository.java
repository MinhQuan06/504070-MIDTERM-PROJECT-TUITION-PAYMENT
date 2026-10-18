package vn.edu.tdtu.ibanking.paymentservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.tdtu.ibanking.paymentservice.entity.Account;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    /**
     * Trừ tiền có điều kiện trên MySQL InnoDB:
     * MySQL sẽ tự động khóa dòng (exclusive row lock) có user_id tương ứng khi chạy UPDATE.
     * Trả về 1 nếu trừ thành công, trả về 0 nếu số dư < amount.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Account a SET a.balance = a.balance - :amount, a.version = a.version + 1 " +
            "WHERE a.userId = :userId AND a.balance >= :amount")
    int deductBalance(@Param("userId") Long userId, @Param("amount") Long amount);

    /**
     * Hoàn tiền (Compensating transaction trong Saga khi Student Service báo lỗi)
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Account a SET a.balance = a.balance + :amount, a.version = a.version + 1 " +
            "WHERE a.userId = :userId")
    int refundBalance(@Param("userId") Long userId, @Param("amount") Long amount);
}