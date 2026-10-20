package vn.edu.tdtu.ibanking.paymentservice.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import java.net.http.HttpClient;
import java.time.Duration;
import vn.edu.tdtu.ibanking.paymentservice.security.PaymentActor;
import org.springframework.web.client.RestClient;
import vn.edu.tdtu.ibanking.paymentservice.dto.UserDto;
import vn.edu.tdtu.ibanking.paymentservice.exception.ResourceNotFoundException;

@Component
@Slf4j
public class UserServiceClient {

    private final RestClient restClient;
    private final boolean mockEnabled;
    private final String internalApiKey;

    public UserServiceClient(
            RestClient.Builder builder,
            @Value("${app.external-services.user-service-url}") String baseUrl,
            @Value("${app.external-services.user-mock-enabled:false}") boolean mockEnabled,
            @Value("${app.external-services.user-internal-api-key:}") String internalApiKey) {
        var requestFactory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3)).build());
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = builder.clone().requestFactory(requestFactory).baseUrl(baseUrl).build();
        this.internalApiKey = internalApiKey;
        this.mockEnabled = mockEnabled;
    }

    public PaymentActor getAuthenticatedUser(String authorization) {
        // Always validate with User, even when contact-data mocks are enabled.
        return restClient.get().uri("/api/v1/users/me")
                .header("Authorization", authorization).retrieve()
                .body(PaymentActor.class);
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
                    .uri("/internal/users/{id}", userId)
                    .header("X-Internal-API-Key", internalApiKey)
                    .retrieve()
                    .body(UserDto.class);
        } catch (Exception e) {
            log.error("Lỗi khi gọi User Service: {}", e.getMessage());
            throw new ResourceNotFoundException("Không thể xác thực thông tin người nộp tiền từ User Service (user_id=" + userId + ")");
        }
    }
}