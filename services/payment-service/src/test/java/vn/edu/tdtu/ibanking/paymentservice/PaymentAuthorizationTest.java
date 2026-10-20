package vn.edu.tdtu.ibanking.paymentservice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import vn.edu.tdtu.ibanking.paymentservice.client.UserServiceClient;
import vn.edu.tdtu.ibanking.paymentservice.controller.AccountController;
import vn.edu.tdtu.ibanking.paymentservice.controller.TransactionController;
import vn.edu.tdtu.ibanking.paymentservice.dto.*;
import vn.edu.tdtu.ibanking.paymentservice.security.*;
import vn.edu.tdtu.ibanking.paymentservice.service.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PaymentAuthorizationTest {
    final UserServiceClient users = mock(UserServiceClient.class);
    final AccountService accounts = mock(AccountService.class);
    final TransactionService transactions = mock(TransactionService.class);
    MockMvc mvc;
    @BeforeEach void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new AccountController(accounts), new TransactionController(transactions))
                .addFilters(new PaymentAuthenticationFilter(users)).build();
        when(users.getAuthenticatedUser("Bearer owner")).thenReturn(new PaymentActor(10L,"student"));
        when(users.getAuthenticatedUser("Bearer admin")).thenReturn(new PaymentActor(20L,"admin"));
        when(transactions.getTransactionById(1L)).thenReturn(TransactionResponse.builder().payerUserId(10L).build());
        when(transactions.getTransactionById(2L)).thenReturn(TransactionResponse.builder().payerUserId(20L).build());
    }
    @Test void missingInvalidAndUnavailableAuthenticationFailClosed() throws Exception {
        mvc.perform(get("/api/v1/accounts/10")).andExpect(status().isUnauthorized());
        when(users.getAuthenticatedUser("Bearer invalid")).thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED));
        mvc.perform(get("/api/v1/accounts/10").header("Authorization","Bearer invalid")).andExpect(status().isUnauthorized());
        when(users.getAuthenticatedUser("Bearer down")).thenThrow(new ResourceAccessException("offline"));
        mvc.perform(get("/api/v1/accounts/10").header("Authorization","Bearer down")).andExpect(status().isServiceUnavailable());
        verifyNoInteractions(accounts, transactions);
    }
    @Test void accountsAndHistoryAreOwnerOnlyEvenForAdmin() throws Exception {
        mvc.perform(get("/api/v1/accounts/10").header("Authorization","Bearer owner")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/accounts/20").header("Authorization","Bearer owner")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/accounts/10").header("Authorization","Bearer admin")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/transactions/user/20").header("Authorization","Bearer owner")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/transactions/user/10").header("Authorization","Bearer owner")).andExpect(status().isOk());
        verify(accounts,never()).getAccountByUserId(20L);
        verify(transactions,never()).getTransactionHistoryByUser(20L);
    }
    @Test void onlyAdminCanSeedBalance() throws Exception {
        var json="{\"userId\":10,\"balance\":5000}";
        mvc.perform(post("/api/v1/accounts").header("Authorization","Bearer owner").contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isForbidden());
        verifyNoInteractions(accounts);
        mvc.perform(post("/api/v1/accounts").header("Authorization","Bearer admin").contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isCreated());
        verify(accounts).createOrUpdateSeedAccount(10L,5000L);
    }
    @Test void payerIsDerivedFromTokenAndSpoofingIsRejected() throws Exception {
        String body="{\"tuitionId\":1,\"studentId\":\"SV001\",\"amount\":1000}";
        mvc.perform(post("/api/v1/transactions").header("Authorization","Bearer owner").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
        verify(transactions).initiateTransaction(argThat(r -> r.getPayerUserId().equals(10L)));
        clearInvocations(transactions);
        mvc.perform(post("/api/v1/transactions").header("Authorization","Bearer owner").contentType(MediaType.APPLICATION_JSON).content(body.replace("{","{\"payerUserId\":20,"))).andExpect(status().isForbidden());
        verifyNoInteractions(transactions);
    }
    @Test void foreignTransactionCannotBeReadResentOrConfirmed() throws Exception {
        mvc.perform(get("/api/v1/transactions/2").header("Authorization","Bearer owner")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/transactions/2/resend-otp").header("Authorization","Bearer owner")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/transactions/2/verify-otp").header("Authorization","Bearer owner").contentType(MediaType.APPLICATION_JSON).content("{\"otpCode\":\"123456\"}")).andExpect(status().isForbidden());
        verify(transactions,never()).resendOtp(anyLong());
        verify(transactions,never()).verifyOtpAndExecuteSaga(anyLong(),anyString());
    }
    @Test void ownerMayReadResendAndConfirm() throws Exception {
        mvc.perform(get("/api/v1/transactions/1").header("Authorization","Bearer owner")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/transactions/1/resend-otp").header("Authorization","Bearer owner")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/transactions/1/verify-otp").header("Authorization","Bearer owner").contentType(MediaType.APPLICATION_JSON).content("{\"otpCode\":\"123456\"}")).andExpect(status().isOk());
        verify(transactions).resendOtp(1L);
        verify(transactions).verifyOtpAndExecuteSaga(1L,"123456");
    }
}
