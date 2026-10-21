package vn.edu.tdtu.ibanking.paymentservice.exception;

// Failed guesses must commit their counter, while debit failures still roll back OTP use.
public class OtpVerificationException extends BusinessException {
    public OtpVerificationException(String message) { super(message); }
}
