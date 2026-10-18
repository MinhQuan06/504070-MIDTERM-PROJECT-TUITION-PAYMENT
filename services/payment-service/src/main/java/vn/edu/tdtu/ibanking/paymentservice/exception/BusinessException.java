package vn.edu.tdtu.ibanking.paymentservice.exception;

public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}