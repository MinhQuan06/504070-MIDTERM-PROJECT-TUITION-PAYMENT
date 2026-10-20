package vn.edu.tdtu.ibanking.paymentservice.controller;

import jakarta.validation.Valid;
import vn.edu.tdtu.ibanking.paymentservice.security.PaymentActor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.edu.tdtu.ibanking.paymentservice.dto.AccountResponse;
import vn.edu.tdtu.ibanking.paymentservice.dto.CreateAccountRequest;
import vn.edu.tdtu.ibanking.paymentservice.service.AccountService;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AccountController {

    private final AccountService accountService;

    /**
     * 1. API có JWT: Chỉ chủ tài khoản được xem số dư theo userId
     * Được sử dụng bởi Web App (Client) thông qua API Gateway.
     */
    @GetMapping("/{userId}")
    public ResponseEntity<AccountResponse> getAccountBalance(@PathVariable Long userId, @RequestAttribute("paymentActor") PaymentActor actor) {
        actor.requireOwner(userId);
        return ResponseEntity.ok(accountService.getAccountByUserId(userId));
    }

    /**
     * 2. API Phục vụ Demo / Admin Seed Data:
     * Khởi tạo tài khoản mới hoặc cập nhật số dư mẫu cho một user_id.
     */
    @PostMapping
    public ResponseEntity<AccountResponse> createOrSeedAccount(
            @Valid @RequestBody CreateAccountRequest request, @RequestAttribute("paymentActor") PaymentActor actor) {
        actor.requireAdmin();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(accountService.createOrUpdateSeedAccount(request.getUserId(), request.getBalance()));
    }

    /*
     * =========================================================================
     * ĐÃ ĐÓNG KHÓA CÁC API NỘI BỘ (/deduct và /refund)
     * Lý do: Việc trừ tiền và hoàn tiền bắt buộc phải đi qua luồng xác thực OTP
     * và điều phối giao dịch Saga trong TransactionService, không mở qua HTTP.
     * =========================================================================
     */
}