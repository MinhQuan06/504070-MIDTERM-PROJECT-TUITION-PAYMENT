package vn.edu.tdtu.ibanking.paymentservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateTransactionRequest {

    @NotNull(message = "payerUserId không được để trống")
    private Long payerUserId;

    @NotNull(message = "tuitionId không được để trống")
    private Long tuitionId;

    @NotBlank(message = "MSSV (studentId) không được để trống")
    private String studentId;

    @NotNull(message = "Số tiền không được để trống")
    @Min(value = 1, message = "Số tiền phải lớn hơn 0")
    private Long amount;
}