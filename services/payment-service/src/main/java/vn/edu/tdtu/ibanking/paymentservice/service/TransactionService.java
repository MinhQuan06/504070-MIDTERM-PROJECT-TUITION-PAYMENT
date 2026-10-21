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
    private final OtpPolicy otpPolicy;
    private final NotificationProducer notificationProducer;
    private final SagaPreparationService sagaPreparation;
    private final PaymentSagaWorker sagaWorker;
    private final UserServiceClient userServiceClient;
    private final StudentServiceClient studentServiceClient;

    /**
     * BƯỚC 1: KHỞI TẠO GIAO DỊCH (PENDING), SINH OTP & BẮN SỰ KIỆN RABBITMQ
     */
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
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
                .studentId(tuition.getStudentId())
                .amount(request.getAmount())
                .status(TransactionStatus.PENDING)
                .tuitionVersion(tuition.getVersion())
                .payerEmail(payer.getEmail()).payerFullName(payer.getFullName())
                .build();

        tx = transactionRepository.save(tx);

        // 1. Sinh OTP 6 chữ số
        otpPolicy.reserveIssuance(tx.getTransactionId());
        OtpService.IssuedOtp issuedOtp = otpService.generateAndSaveOtp(tx.getTransactionId());

        // 2. Bắn sự kiện gửi email OTP qua RabbitMQ
        notificationProducer.sendOtpEmailEvent(OtpEmailEvent.builder()
                .eventId(issuedOtp.eventId())
                .type("OtpRequested")
                .transactionId(tx.getTransactionId().toString())
                .email(payer.getEmail())
                .fullName(payer.getFullName())
                .otp(issuedOtp.code())
                .expiresAt(issuedOtp.expiresAt())
                .build());

        return mapToResponse(tx, payer.getEmail(),
                "Giao dịch đã được khởi tạo. Yêu cầu gửi OTP đã được ghi nhận cho email " + payer.getEmail() + " (hiệu lực 5 phút).");
    }

    /**
     * CẤP LẠI MÃ OTP MỚI (RESEND OTP) & BẮN SỰ KIỆN RABBITMQ
     */
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public TransactionResponse resendOtp(Long transactionId) {
        Transaction tx = transactionRepository.findByIdForUpdate(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giao dịch: " + transactionId));

        if (tx.getStatus() != TransactionStatus.PENDING) {
            throw new BusinessException("Chỉ có thể cấp lại OTP cho giao dịch đang ở trạng thái PENDING.");
        }

        UserDto payer = userServiceClient.getUserById(tx.getPayerUserId());

        // 1. Vô hiệu hóa OTP cũ và sinh OTP mới
        otpPolicy.reserveIssuance(tx.getTransactionId());
        OtpService.IssuedOtp issuedOtp = otpService.generateAndSaveOtp(tx.getTransactionId());

        // 2. Bắn sự kiện gửi lại email OTP qua RabbitMQ
        notificationProducer.sendOtpEmailEvent(OtpEmailEvent.builder()
                .eventId(issuedOtp.eventId())
                .type("OtpRequested")
                .transactionId(tx.getTransactionId().toString())
                .email(payer.getEmail())
                .fullName(payer.getFullName())
                .otp(issuedOtp.code())
                .expiresAt(issuedOtp.expiresAt())
                .build());

        return mapToResponse(tx, payer.getEmail(),
                "Đã cấp lại OTP và ghi nhận yêu cầu gửi tới email " + payer.getEmail() + " (hiệu lực 5 phút).");
    }

    /**
     * BƯỚC 2: XÁC THỰC OTP VÀ THỰC THI SAGA THANH TOÁN
     */
    public TransactionResponse verifyOtpAndExecuteSaga(Long transactionId, String otpCode) {
        Transaction tx = sagaPreparation.prepare(transactionId, otpCode);
        if (tx.getStatus() == TransactionStatus.PROCESSING) tx = sagaWorker.process(transactionId);
        if (tx.getStatus() == TransactionStatus.FAILED)
            throw new BusinessException("Student rejected this payment. Funds have been refunded.");
        return mapToResponse(tx, null, tx.getStatus() == TransactionStatus.PROCESSING
                ? "Payment is being reconciled; do not create a replacement payment." : "Payment completed successfully.");
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