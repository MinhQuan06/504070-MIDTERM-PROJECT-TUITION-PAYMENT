package vn.edu.tdtu.ibanking.paymentservice.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import vn.edu.tdtu.ibanking.paymentservice.dto.UserDto;
import vn.edu.tdtu.ibanking.paymentservice.exception.ResourceNotFoundException;

@Component
@Slf4j
public class UserServiceClient {

    private final RestClient restClient;
    private final boolean mockEnabled;

    public UserServiceClient(
            RestClient.Builder builder,
            @Value("${app.external-services.user-service-url}") String baseUrl,
            @Value("${app.external-services.user-mock-enabled:false}") boolean mockEnabled) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.mockEnabled = mockEnabled;
    }

    public UserDto getUserById(Long userId) {
        if (mockEnabled) {
            log.info("[MOCK] Đang lấy thông tin User từ User Service cho user_id={}", userId);
            return UserDto.builder()
                    .userId(userId)
                    .fullName("Nguyễn Văn A (User #" + userId + ")")
                    .email("user" + userId + "@tdtu.edu.vn")
                    .phone("0901234567")
                    .build();
        }

        try {
            return restClient.get()
                    .uri("/api/v1/users/{id}", userId)
                    .retrieve()
                    .body(UserDto.class);
        } catch (Exception e) {
            log.error("Lỗi khi gọi User Service: {}", e.getMessage());
            throw new ResourceNotFoundException("Không thể xác thực thông tin người nộp tiền từ User Service (user_id=" + userId + ")");
        }
    }
}