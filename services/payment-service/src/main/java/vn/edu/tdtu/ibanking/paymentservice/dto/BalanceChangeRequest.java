package vn.edu.tdtu.ibanking.paymentservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BalanceChangeRequest {
    @NotNull(message = "Số tiền không được để trống")
    @Min(value = 1, message = "Số tiền phải lớn hơn 0")
    private Long amount;
}