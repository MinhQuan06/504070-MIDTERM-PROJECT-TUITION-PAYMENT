package vn.edu.tdtu.ibanking.paymentservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.tdtu.ibanking.paymentservice.entity.Otp;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface OtpRepository extends JpaRepository<Otp, Long> {

    /**
     * Kiểm tra xem mã băm (code_hash) có bị trùng với bất kỳ OTP nào đang còn hiệu lực không
     * (Thiết kế mục 4.1: "khi sinh phải kiểm tra không trùng OTP còn hiệu lực")
     */
    boolean existsByCodeHashAndUsedAtIsNullAndExpiresAtAfter(String codeHash, LocalDateTime now);

    /**
     * Vô hiệu hóa tất cả OTP cũ chưa dùng của một giao dịch (khi cấp lại OTP mới - Resend OTP)
     * Đảm bảo tại một thời điểm chỉ duy nhất 1 OTP có hiệu lực cho mỗi giao dịch.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Otp o SET o.expiresAt = :now " +
            "WHERE o.transactionId = :txId AND o.usedAt IS NULL AND o.expiresAt > :now")
    int invalidateActiveOtps(@Param("txId") Long transactionId, @Param("now") LocalDateTime now);

    /**
     * Lấy OTP mới nhất chưa sử dụng và còn hạn của giao dịch
     */
    Optional<Otp> findFirstByTransactionIdAndUsedAtIsNullAndExpiresAtAfterOrderByOtpIdDesc(
            Long transactionId, LocalDateTime now);

    /**
     * Chống Race Condition khi xác thực OTP 2 lần cùng lúc (Thiết kế mục 4.2):
     * UPDATE otps SET used_at = now() WHERE used_at IS NULL AND expires_at > now() AND transaction_id = :tx_id
     * Trả về 1 nếu đánh dấu thành công, trả về 0 nếu OTP đã được dùng hoặc hết hạn.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Otp o SET o.usedAt = :now " +
            "WHERE o.transactionId = :txId AND o.codeHash = :codeHash " +
            "AND o.usedAt IS NULL AND o.expiresAt > :now")
    int consumeOtpConditionally(
            @Param("txId") Long transactionId,
            @Param("codeHash") String codeHash,
            @Param("now") LocalDateTime now);
}