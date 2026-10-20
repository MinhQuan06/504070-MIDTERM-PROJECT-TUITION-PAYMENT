package vn.edu.tdtu.ibanking.paymentservice;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import vn.edu.tdtu.ibanking.paymentservice.client.*;
import vn.edu.tdtu.ibanking.paymentservice.entity.*;
import vn.edu.tdtu.ibanking.paymentservice.repository.TransactionRepository;
import vn.edu.tdtu.ibanking.paymentservice.service.*;
import vn.edu.tdtu.ibanking.paymentservice.exception.BusinessException;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDateTime;

@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="SAGA_TEST_DATABASE", matches="true")
@SpringBootTest(properties={"app.saga.recovery-enabled=false", "app.outbox.enabled=false", "app.outbox.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="})
class SagaIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionRepository repository;
    @Autowired SagaPreparationService preparation;
    @Autowired PaymentSagaWorker worker;
    @Autowired OtpService otp;
    @Autowired AccountService accounts;
    @MockitoBean StudentServiceClient students;
    @MockitoBean UserServiceClient users;
    long payer;
    Transaction tx;
    String code;
    final java.util.List<Long> extraTransactions = new java.util.ArrayList<>();
    @BeforeEach void fixture() {
        assertEquals("payment_saga_test",jdbc.queryForObject("SELECT DATABASE()",String.class));
        payer = System.currentTimeMillis();
        accounts.createOrUpdateSeedAccount(payer,5000L);
        tx=repository.saveAndFlush(Transaction.builder().payerUserId(payer).tuitionId(payer).studentId("SAGA")
                .amount(1000L).tuitionVersion(0).payerEmail("saga@example.com").payerFullName("Saga test")
                .status(TransactionStatus.PENDING).build());
        code=otp.generateAndSaveOtp(tx.getTransactionId()).code();
    }
    @AfterEach void cleanup() {
        if(tx==null)return;
        for (Long id : extraTransactions) {
            jdbc.update("DELETE FROM otps WHERE transaction_id=?",id);
            repository.deleteById(id);
        }
        jdbc.update("DELETE FROM outbox_events WHERE transaction_id=?",tx.getTransactionId());
        jdbc.update("DELETE FROM otps WHERE transaction_id=?",tx.getTransactionId());
        repository.deleteById(tx.getTransactionId());
        jdbc.update("DELETE FROM accounts WHERE user_id=?",payer);
    }
    void outcome(StudentServiceClient.PaymentResult result) {
        when(students.markTuitionAsPaid(tx.getTuitionId(),tx.getTransactionId(),"SAGA",1000L,0)).thenReturn(result);
    }
    Transaction stored(){return repository.findById(tx.getTransactionId()).orElseThrow();}
    void due(){jdbc.update("UPDATE transactions SET next_attempt_at=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE transaction_id=?",tx.getTransactionId());}
    @Test void concurrentOtpIssuanceAcrossTransactionsDoesNotDeadlock() throws Exception {
        for (int i=0;i<6;i++) extraTransactions.add(repository.saveAndFlush(Transaction.builder()
                .payerUserId(payer).tuitionId(payer+i+1).studentId("SAGA").amount(1000L)
                .status(TransactionStatus.PENDING).build()).getTransactionId());
        var executor=java.util.concurrent.Executors.newFixedThreadPool(6);
        try {
            var futures=extraTransactions.stream().map(id -> executor.submit(() -> otp.generateAndSaveOtp(id).code())).toList();
            var codes=new java.util.HashSet<String>();
            for(var future:futures) assertTrue(codes.add(future.get(15,java.util.concurrent.TimeUnit.SECONDS)));
            assertEquals(6,codes.size());
        } finally {executor.shutdownNow();}
    }
    @Test void otpDebitAndProcessingAreAtomicAndRepeatedConfirmationDoesNotDebit() {
        assertThrows(BusinessException.class,()->preparation.prepare(tx.getTransactionId(),"000000"));
        assertEquals(5000,accounts.getAccountByUserId(payer).getBalance());
        assertEquals(TransactionStatus.PENDING,stored().getStatus());
        preparation.prepare(tx.getTransactionId(),code);
        preparation.prepare(tx.getTransactionId(),code);
        assertEquals(4000,accounts.getAccountByUserId(payer).getBalance());
        outcome(StudentServiceClient.PaymentResult.APPLIED);
        worker.process(tx.getTransactionId());worker.process(tx.getTransactionId());
        assertEquals(TransactionStatus.SUCCESS,stored().getStatus());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events WHERE event_id=?",Integer.class,"payment-succeeded-"+tx.getTransactionId()));
    }
    @Test void insufficientBalanceRollsBackOtpConsumption() {
        accounts.createOrUpdateSeedAccount(payer,500L);
        assertThrows(RuntimeException.class,()->preparation.prepare(tx.getTransactionId(),code));
        assertEquals(TransactionStatus.PENDING,stored().getStatus());
        accounts.createOrUpdateSeedAccount(payer,5000L);
        preparation.prepare(tx.getTransactionId(),code);
        assertEquals(TransactionStatus.PROCESSING,stored().getStatus());
        assertEquals(4000,accounts.getAccountByUserId(payer).getBalance());
    }
    @Test void uncertainOutcomeRemainsDurableThenRecoversWithoutRefundOrSecondDebit() {
        preparation.prepare(tx.getTransactionId(),code);
        outcome(StudentServiceClient.PaymentResult.UNKNOWN);
        worker.process(tx.getTransactionId());
        assertEquals(TransactionStatus.PROCESSING,stored().getStatus());
        assertEquals(4000,accounts.getAccountByUserId(payer).getBalance());
        assertEquals(1,stored().getSagaAttempts());
        outcome(StudentServiceClient.PaymentResult.APPLIED);due();
        assertTrue(worker.processNext());
        assertEquals(TransactionStatus.SUCCESS,stored().getStatus());
        assertEquals(4000,accounts.getAccountByUserId(payer).getBalance());
    }
    @Test void explicitRejectionRefundsOnlyOnce() {
        preparation.prepare(tx.getTransactionId(),code);
        outcome(StudentServiceClient.PaymentResult.REJECTED);
        worker.process(tx.getTransactionId());worker.process(tx.getTransactionId());
        assertEquals(TransactionStatus.FAILED,stored().getStatus());
        assertEquals(5000,accounts.getAccountByUserId(payer).getBalance());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events WHERE transaction_id=?",Integer.class,tx.getTransactionId()));
    }
}
