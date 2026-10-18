package vn.edu.tdtu.ibanking.paymentservice.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AccountResponse {
    private Long userId;
    private Long balance;
    private Integer version;
}