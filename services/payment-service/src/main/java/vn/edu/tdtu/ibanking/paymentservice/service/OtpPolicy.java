package vn.edu.tdtu.ibanking.paymentservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import vn.edu.tdtu.ibanking.paymentservice.exception.OtpVerificationException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY, noRollbackFor = OtpVerificationException.class)
public class OtpPolicy {
    private final JdbcTemplate jdbc;

    public void reserveIssuance(Long id) {
        var row = jdbc.queryForMap("SELECT otp_issued_at, otp_resend_count FROM transactions WHERE transaction_id=? FOR UPDATE", id);
        var rawIssued = row.get("otp_issued_at");
        LocalDateTime issued = rawIssued instanceof java.sql.Timestamp ts ? ts.toLocalDateTime() : (LocalDateTime) rawIssued;
        var now = LocalDateTime.now(ZoneOffset.UTC);
        if (issued != null) {
            if (((Number) row.get("otp_resend_count")).intValue() >= 5)
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Đã đạt giới hạn 5 lần gửi lại OTP cho giao dịch này.");
            if (issued.plusSeconds(60).isAfter(now))
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Vui lòng đợi 60 giây giữa hai lần cấp OTP.");
        }
        jdbc.update("UPDATE transactions SET otp_failed_attempts=0, otp_resend_count=otp_resend_count+?, otp_issued_at=? WHERE transaction_id=?",
                issued == null ? 0 : 1, now, id);
    }

    public void requireAttemptsAvailable(Long id) {
        Integer attempts = jdbc.queryForObject("SELECT otp_failed_attempts FROM transactions WHERE transaction_id=? FOR UPDATE", Integer.class, id);
        if (attempts != null && attempts >= 5)
            throw new OtpVerificationException("Mã OTP đã bị khóa sau 5 lần nhập sai. Vui lòng yêu cầu mã mới.");
    }

    public void recordFailure(Long id) {
        jdbc.update("UPDATE transactions SET otp_failed_attempts=otp_failed_attempts+1 WHERE transaction_id=?", id);
    }
}
