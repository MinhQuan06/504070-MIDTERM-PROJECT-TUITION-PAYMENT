package vn.edu.tdtu.ibanking.paymentservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.tdtu.ibanking.paymentservice.entity.Otp;
import vn.edu.tdtu.ibanking.paymentservice.exception.BusinessException;
import vn.edu.tdtu.ibanking.paymentservice.repository.OtpRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

    public record IssuedOtp(String eventId, String code, Instant expiresAt) {}

    private static final int OTP_EXPIRY_MINUTES = 5;
    private final OtpRepository otpRepository;
    private final OtpPolicy policy;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Sinh mã OTP 6 chữ số mới cho giao dịch:
     * 1. Vô hiệu hóa các OTP cũ (nếu có) của cùng transactionId.
     * 2. Sinh mã 6 số ngẫu nhiên, băm SHA-256 và kiểm tra không trùng với OTP đang còn hiệu lực.
     * 3. Lưu vào DB với thời hạn 5 phút.
     * 4. Trả về mã OTP gốc (plain text) để gửi qua RabbitMQ cho Notification Service.
     */
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public IssuedOtp generateAndSaveOtp(Long transactionId) {
        otpRepository.lockIssuance();
        LocalDateTime now = LocalDateTime.now();

        // 1. Vô hiệu hóa các OTP cũ còn hiệu lực của giao dịch này
        otpRepository.invalidateActiveOtps(transactionId, now);

        // 2. Sinh OTP 6 chữ số và đảm bảo không trùng với OTP đang còn hiệu lực
        String rawOtp;
        String codeHash;
        do {
            int number = 100000 + secureRandom.nextInt(900000); // Từ 100000 đến 999999
            rawOtp = String.valueOf(number);
            codeHash = hashOtp(rawOtp);
        } while (otpRepository.existsByCodeHashAndUsedAtIsNullAndExpiresAtAfter(codeHash, now));

        // 3. Lưu OTP mới với thời hạn 5 phút
        String eventId = UUID.randomUUID().toString();
        Otp otp = Otp.builder()
                .eventId(eventId)
                .transactionId(transactionId)
                .codeHash(codeHash)
                .expiresAt(now.plusMinutes(OTP_EXPIRY_MINUTES))
                .usedAt(null)
                .build();

        otpRepository.save(otp);

        log.info("OTP issued for transactionId={}", transactionId);
        return new IssuedOtp(eventId, rawOtp,
                otp.getExpiresAt().atZone(ZoneId.systemDefault()).toInstant());
    }

    /**
     * Xác thực và đánh dấu đã sử dụng OTP bằng cập nhật có điều kiện (chống nhập 2 lần cùng lúc)
     */
    @Transactional(noRollbackFor = vn.edu.tdtu.ibanking.paymentservice.exception.OtpVerificationException.class)
    public void verifyAndConsumeOtp(Long transactionId, String rawOtpCode) {
        LocalDateTime now = LocalDateTime.now();
        policy.requireAttemptsAvailable(transactionId);
        String codeHash = hashOtp(rawOtpCode);

        // Thực hiện câu lệnh UPDATE có điều kiện trên DB
        int updatedRows = otpRepository.consumeOtpConditionally(transactionId, codeHash, now);

        if (updatedRows == 0) {
            log.warn("Xác thực OTP thất bại cho transactionId={}", transactionId);
            policy.recordFailure(transactionId);
            throw new vn.edu.tdtu.ibanking.paymentservice.exception.OtpVerificationException("Mã OTP không chính xác, đã hết hạn (5 phút) hoặc đã được sử dụng.");
        }

        log.info("Xác thực OTP thành công cho transactionId={}", transactionId);
    }

    /**
     * Hàm băm mã OTP bằng SHA-256
     */
    private String hashOtp(String rawOtp) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = digest.digest(rawOtp.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(encodedHash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Không tìm thấy thuật toán SHA-256", e);
        }
    }
}