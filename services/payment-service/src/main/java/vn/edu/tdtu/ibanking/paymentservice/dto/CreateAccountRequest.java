package vn.edu.tdtu.ibanking.paymentservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateAccountRequest {

    @NotNull(message = "userId không được để trống")
    private Long userId;

    @NotNull(message = "Số dư khởi tạo không được để trống")
    @Min(value = 0, message = "Số dư không được âm (balance >= 0)")
    private Long balance;
}