package vn.edu.tdtu.ibanking.paymentservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.tdtu.ibanking.paymentservice.client.StudentServiceClient;
import vn.edu.tdtu.ibanking.paymentservice.client.UserServiceClient;
import vn.edu.tdtu.ibanking.paymentservice.dto.*;
import vn.edu.tdtu.ibanking.paymentservice.entity.Transaction;
import vn.edu.tdtu.ibanking.paymentservice.entity.TransactionStatus;
import vn.edu.tdtu.ibanking.paymentservice.exception.BusinessException;
import vn.edu.tdtu.ibanking.paymentservice.exception.InsufficientBalanceException;
import vn.edu.tdtu.ibanking.paymentservice.exception.ResourceNotFoundException;
import vn.edu.tdtu.ibanking.paymentservice.repository.TransactionRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountService accountService;
    private final OtpService otpService;
    private final NotificationProducer notificationProducer;
    private final UserServiceClient userServiceClient;
    private final StudentServiceClient studentServiceClient;

    /**
     * BƯỚC 1: KHỞI TẠO GIAO DỊCH (PENDING), SINH OTP & BẮN SỰ KIỆN RABBITMQ
     */
    @Transactional
    public TransactionResponse initiateTransaction(CreateTransactionRequest request) {
        UserDto payer = userServiceClient.getUserById(request.getPayerUserId());

        AccountResponse account = accountService.getAccountByUserId(request.getPayerUserId());
        if (account.getBalance() < request.getAmount()) {
            throw new InsufficientBalanceException("Số dư tài khoản không đủ để khởi tạo giao dịch.");
        }

        TuitionDto tuition = studentServiceClient.getTuitionById(
                request.getTuitionId(), request.getStudentId(), request.getAmount());

        if (tuition == null) {
            throw new ResourceNotFoundException("Không tìm thấy khoản học phí với ID: " + request.getTuitionId());
        }
        if (!tuition.getStudentId().equalsIgnoreCase(request.getStudentId())) {
            throw new BusinessException("Mã số sinh viên không khớp với khoản học phí.");
        }
        if ("PAID".equalsIgnoreCase(tuition.getStatus())) {
            throw new BusinessException("Khoản học phí này đã được thanh toán trước đó.");
        }
        if (!tuition.getAmount().equals(request.getAmount())) {
            throw new BusinessException("Số tiền thanh toán phải bằng đúng số tiền học phí: " + tuition.getAmount());
        }

        Transaction tx = Transaction.builder()
                .payerUserId(request.getPayerUserId())
                .tuitionId(request.getTuitionId())
                .studentId(request.getStudentId())
                .amount(request.getAmount())
                .status(TransactionStatus.PENDING)
                .build();

        tx = transactionRepository.save(tx);

        // 1. Sinh OTP 6 chữ số
        String rawOtp = otpService.generateAndSaveOtp(tx.getTransactionId());

        // 2. Bắn sự kiện gửi email OTP qua RabbitMQ
        notificationProducer.sendOtpEmailEvent(OtpEmailEvent.builder()
                .eventType("OTP_GENERATED")
                .transactionId(tx.getTransactionId())
                .recipientEmail(payer.getEmail())
                .payerFullName(payer.getFullName())
                .studentId(tx.getStudentId())
                .amount(tx.getAmount())
                .otpCode(rawOtp)
                .expiryMinutes(5)
                .timestamp(LocalDateTime.now())
                .build());

        return mapToResponse(tx, payer.getEmail(),
                "Giao dịch đã được khởi tạo. Mã OTP đã được gửi tới email " + payer.getEmail() + " (hiệu lực 5 phút).");
    }

    /**
     * CẤP LẠI MÃ OTP MỚI (RESEND OTP) & BẮN SỰ KIỆN RABBITMQ
     */
    @Transactional
    public TransactionResponse resendOtp(Long transactionId) {
        Transaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giao dịch: " + transactionId));

        if (tx.getStatus() != TransactionStatus.PENDING) {
            throw new BusinessException("Chỉ có thể cấp lại OTP cho giao dịch đang ở trạng thái PENDING.");
        }

        UserDto payer = userServiceClient.getUserById(tx.getPayerUserId());

        // 1. Vô hiệu hóa OTP cũ và sinh OTP mới
        String rawOtp = otpService.generateAndSaveOtp(tx.getTransactionId());

        // 2. Bắn sự kiện gửi lại email OTP qua RabbitMQ
        notificationProducer.sendOtpEmailEvent(OtpEmailEvent.builder()
                .eventType("OTP_GENERATED")
                .transactionId(tx.getTransactionId())
                .recipientEmail(payer.getEmail())
                .payerFullName(payer.getFullName())
                .studentId(tx.getStudentId())
                .amount(tx.getAmount())
                .otpCode(rawOtp)
                .expiryMinutes(5)
                .timestamp(LocalDateTime.now())
                .build());

        return mapToResponse(tx, payer.getEmail(),
                "Đã cấp lại mã OTP mới và gửi tới email " + payer.getEmail() + " (hiệu lực 5 phút).");
    }

    /**
     * BƯỚC 2: XÁC THỰC OTP VÀ THỰC THI SAGA THANH TOÁN
     */
    public TransactionResponse verifyOtpAndExecuteSaga(Long transactionId, String otpCode) {
        Transaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giao dịch: " + transactionId));

        if (tx.getStatus() == TransactionStatus.SUCCESS) {
            return mapToResponse(tx, null, "Giao dịch đã được xử lý thành công trước đó (Idempotent).");
        }
        if (tx.getStatus() == TransactionStatus.FAILED) {
            throw new BusinessException("Giao dịch này đã thất bại trước đó, vui lòng tạo giao dịch mới.");
        }

        otpService.verifyAndConsumeOtp(transactionId, otpCode);

        return executePaymentSaga(transactionId);
    }

    /**
     * LUỒNG SAGA CÓ BÙ TRỪ & GỬI EMAIL XÁC NHẬN KHI THÀNH CÔNG
     */
    public TransactionResponse executePaymentSaga(Long transactionId) {
        Transaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giao dịch: " + transactionId));

        if (tx.getStatus() == TransactionStatus.SUCCESS) {
            return mapToResponse(tx, null, "Giao dịch đã được xử lý thành công trước đó (Idempotent).");
        }
        if (tx.getStatus() == TransactionStatus.FAILED) {
            throw new BusinessException("Giao dịch này đã thất bại và kết thúc trước đó.");
        }

        // Bước Saga 1: Trừ tiền cục bộ tại Payment DB
        AccountResponse updatedAccount;
        try {
            updatedAccount = accountService.deductBalance(tx.getPayerUserId(), tx.getAmount());
        } catch (InsufficientBalanceException ex) {
            tx.setStatus(TransactionStatus.FAILED);
            tx.setCompletedAt(LocalDateTime.now());
            transactionRepository.save(tx);
            throw ex;
        }

        // Bước Saga 2: Gọi sang Student Service để đánh dấu học phí PAID
        boolean markedPaid = studentServiceClient.markTuitionAsPaid(tx.getTuitionId(), tx.getTransactionId());

        // Bước Saga 3: Bù trừ (Hoàn tiền) nếu Student Service từ chối
        if (!markedPaid) {
            log.warn("Saga Compensation: Gạch nợ thất bại cho txId={}. Hoàn tiền {} VND cho userId={}",
                    tx.getTransactionId(), tx.getAmount(), tx.getPayerUserId());

            accountService.refundBalance(tx.getPayerUserId(), tx.getAmount());

            tx.setStatus(TransactionStatus.FAILED);
            tx.setCompletedAt(LocalDateTime.now());
            transactionRepository.save(tx);

            throw new BusinessException("Khoản học phí vừa được thanh toán bởi người khác. Hệ thống đã hoàn lại tiền vào tài khoản của bạn.");
        }

        // Bước Saga 4: Thành công hoàn toàn -> Lưu trạng thái SUCCESS
        tx.setStatus(TransactionStatus.SUCCESS);
        tx.setCompletedAt(LocalDateTime.now());
        tx = transactionRepository.save(tx);

        // Bước Saga 5: Bắn sự kiện gửi Email xác nhận biên lai qua RabbitMQ
        UserDto payer = userServiceClient.getUserById(tx.getPayerUserId());
        notificationProducer.sendPaymentSuccessEmailEvent(PaymentSuccessEmailEvent.builder()
                .eventType("PAYMENT_SUCCESS")
                .transactionId(tx.getTransactionId())
                .recipientEmail(payer.getEmail())
                .payerFullName(payer.getFullName())
                .studentId(tx.getStudentId())
                .tuitionId(tx.getTuitionId())
                .amount(tx.getAmount())
                .remainingBalance(updatedAccount.getBalance())
                .completedAt(tx.getCompletedAt())
                .build());

        log.info("Saga Hoàn tất: Giao dịch txId={} thành công (SUCCESS)!", tx.getTransactionId());
        return mapToResponse(tx, payer.getEmail(), "Xác thực OTP và thanh toán học phí thành công!");
    }

    @Transactional(readOnly = true)
    public TransactionResponse getTransactionById(Long transactionId) {
        Transaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giao dịch: " + transactionId));
        return mapToResponse(tx, null, null);
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> getTransactionHistoryByUser(Long userId) {
        return transactionRepository.findByPayerUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(tx -> mapToResponse(tx, null, null))
                .toList();
    }

    private TransactionResponse mapToResponse(Transaction tx, String email, String message) {
        return TransactionResponse.builder()
                .transactionId(tx.getTransactionId())
                .payerUserId(tx.getPayerUserId())
                .payerEmail(email)
                .tuitionId(tx.getTuitionId())
                .studentId(tx.getStudentId())
                .amount(tx.getAmount())
                .status(tx.getStatus())
                .createdAt(tx.getCreatedAt())
                .completedAt(tx.getCompletedAt())
                .message(message)
                .build();
    }
}