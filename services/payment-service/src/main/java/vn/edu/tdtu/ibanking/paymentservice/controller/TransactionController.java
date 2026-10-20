package vn.edu.tdtu.ibanking.paymentservice.controller;

import jakarta.validation.Valid;
import vn.edu.tdtu.ibanking.paymentservice.security.PaymentActor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.edu.tdtu.ibanking.paymentservice.dto.CreateTransactionRequest;
import vn.edu.tdtu.ibanking.paymentservice.dto.TransactionResponse;
import vn.edu.tdtu.ibanking.paymentservice.dto.VerifyOtpRequest;
import vn.edu.tdtu.ibanking.paymentservice.service.TransactionService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    // 1. API Khởi tạo giao dịch & tự động sinh OTP
    @PostMapping
    public ResponseEntity<TransactionResponse> initiateTransaction(
            @Valid @RequestBody CreateTransactionRequest request, @RequestAttribute("paymentActor") PaymentActor actor) {
        if (request.getPayerUserId() != null) actor.requireOwner(request.getPayerUserId());
        request.setPayerUserId(actor.userId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transactionService.initiateTransaction(request));
    }

    // 2. API Xác thực OTP và thực thi thanh toán Saga
    @PostMapping("/{transactionId}/verify-otp")
    public ResponseEntity<TransactionResponse> verifyOtpAndPay(
            @PathVariable Long transactionId,
            @Valid @RequestBody VerifyOtpRequest request, @RequestAttribute("paymentActor") PaymentActor actor) {
        requireTransactionOwner(transactionId, actor);
        var result = transactionService.verifyOtpAndExecuteSaga(transactionId, request.getOtpCode());
        return ResponseEntity.status(result != null && result.getStatus() == vn.edu.tdtu.ibanking.paymentservice.entity.TransactionStatus.PROCESSING
                ? HttpStatus.ACCEPTED : HttpStatus.OK).body(result);
    }

    // 3. API Cấp lại mã OTP mới (Resend OTP)
    @PostMapping("/{transactionId}/resend-otp")
    public ResponseEntity<TransactionResponse> resendOtp(
            @PathVariable Long transactionId, @RequestAttribute("paymentActor") PaymentActor actor) {
        requireTransactionOwner(transactionId, actor);
        return ResponseEntity.ok(transactionService.resendOtp(transactionId));
    }

    // 4. API Xem chi tiết 1 giao dịch
    @GetMapping("/{transactionId}")
    public ResponseEntity<TransactionResponse> getTransactionById(
            @PathVariable Long transactionId, @RequestAttribute("paymentActor") PaymentActor actor) {
        requireTransactionOwner(transactionId, actor);
        return ResponseEntity.ok(transactionService.getTransactionById(transactionId));
    }

    // 5. API Xem lịch sử giao dịch của người dùng
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<TransactionResponse>> getUserTransactions(
            @PathVariable Long userId, @RequestAttribute("paymentActor") PaymentActor actor) {
        actor.requireOwner(userId);
        return ResponseEntity.ok(transactionService.getTransactionHistoryByUser(userId));
    }
    private void requireTransactionOwner(Long id, PaymentActor actor) {
        actor.requireOwner(transactionService.getTransactionById(id).getPayerUserId());
    }
}
