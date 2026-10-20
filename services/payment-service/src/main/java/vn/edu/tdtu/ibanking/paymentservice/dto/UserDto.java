package vn.edu.tdtu.ibanking.paymentservice.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {
    @JsonAlias("user_id")
    private Long userId;
    @JsonAlias("full_name")
    private String fullName;
    private String email;
    private String phone;
}