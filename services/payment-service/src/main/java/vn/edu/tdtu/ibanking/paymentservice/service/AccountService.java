package vn.edu.tdtu.ibanking.paymentservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.tdtu.ibanking.paymentservice.dto.AccountResponse;
import vn.edu.tdtu.ibanking.paymentservice.entity.Account;
import vn.edu.tdtu.ibanking.paymentservice.exception.InsufficientBalanceException;
import vn.edu.tdtu.ibanking.paymentservice.exception.ResourceNotFoundException;
import vn.edu.tdtu.ibanking.paymentservice.repository.AccountRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final AccountRepository accountRepository;

    @Transactional(readOnly = true)
    public AccountResponse getAccountByUserId(Long userId) {
        Account account = accountRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản cho user_id: " + userId));

        return AccountResponse.builder()
                .userId(account.getUserId())
                .balance(account.getBalance())
                .version(account.getVersion())
                .build();
    }

    /**
     * Phục vụ trang Admin nhập dữ liệu mẫu cho demo (Mục 1.1 trong thiết kế):
     * Nếu user_id chưa có tài khoản thì tạo mới, nếu đã có thì cập nhật lại số dư mẫu.
     */
    @Transactional
    public AccountResponse createOrUpdateSeedAccount(Long userId, Long initialBalance) {
        Account account = accountRepository.findById(userId)
                .orElse(Account.builder()
                        .userId(userId)
                        .balance(initialBalance)
                        .build());

        account.setBalance(initialBalance);
        account = accountRepository.save(account);

        log.info("Đã khởi tạo/cập nhật số dư mẫu cho userId={} với balance={} VND", userId, initialBalance);
        return AccountResponse.builder()
                .userId(account.getUserId())
                .balance(account.getBalance())
                .version(account.getVersion())
                .build();
    }

    /**
     * [NỘI BỘ] Chỉ được gọi bởi TransactionService trong luồng Saga
     */
    @Transactional
    public AccountResponse deductBalance(Long userId, Long amount) {
        if (!accountRepository.existsById(userId)) {
            throw new ResourceNotFoundException("Không tìm thấy tài khoản cho user_id: " + userId);
        }

        int updatedRows = accountRepository.deductBalance(userId, amount);
        if (updatedRows == 0) {
            log.warn("Trừ tiền thất bại do không đủ số dư: userId={}, amount={}", userId, amount);
            throw new InsufficientBalanceException("Số dư tài khoản không đủ để thực hiện giao dịch.");
        }

        log.info("Đã trừ {} VND từ tài khoản userId={}", amount, userId);
        return getAccountByUserId(userId);
    }

    /**
     * [NỘI BỘ] Chỉ được gọi bởi TransactionService khi bù trừ (hoàn tiền) Saga
     */
    @Transactional
    public AccountResponse refundBalance(Long userId, Long amount) {
        int updatedRows = accountRepository.refundBalance(userId, amount);
        if (updatedRows == 0) {
            throw new ResourceNotFoundException("Không thể hoàn tiền. Không tìm thấy tài khoản cho user_id: " + userId);
        }

        log.info("Đã hoàn {} VND vào tài khoản userId={}", amount, userId);
        return getAccountByUserId(userId);
    }
}