package vn.edu.tdtu.ibanking.paymentservice.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import vn.edu.tdtu.ibanking.paymentservice.dto.TuitionDto;
import vn.edu.tdtu.ibanking.paymentservice.exception.ResourceNotFoundException;

import java.util.LinkedHashMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import java.net.http.HttpClient;
import java.time.Duration;

@Component
@Slf4j
public class StudentServiceClient {

    private final RestClient restClient;
    private final boolean mockEnabled;
    private final String internalApiKey;

    public StudentServiceClient(
            RestClient.Builder builder,
            @Value("${app.external-services.student-service-url:http://localhost:5071}") String baseUrl,
            @Value("${app.external-services.student-mock-enabled:false}") boolean mockEnabled,
            @Value("${app.external-services.user-internal-api-key:}") String internalApiKey) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = builder.clone().requestFactory(factory).baseUrl(baseUrl).build();
        this.mockEnabled = mockEnabled;
        this.internalApiKey = internalApiKey;
    }

    /**
     * Gọi GET http://localhost:5071/api/Tuition/{id}
     * (Server C# trả về Status 200 hoặc 201 đều thuộc nhóm 2xx nên RestClient đọc bình thường)
     */
    public TuitionDto getTuitionById(Long tuitionId, String studentId, Long expectedAmount) {
        if (mockEnabled) {
            log.info("[MOCK] Kiểm tra học phí từ Student Service: tuitionId={}, studentId={}", tuitionId, studentId);
            String mockStatus = (tuitionId == 999L) ? "PAID" : "UNPAID";
            return TuitionDto.builder()
                    .tuitionId(tuitionId)
                    .studentId(studentId)
                    .semester("HK2-2025")
                    .amount(expectedAmount)
                    .status(mockStatus)
                    .version(0)
                    .build();
        }

        try {
            log.info("Đang gọi Student Service: GET /api/Tuition/{}", tuitionId);
            return restClient.get()
                    .uri("/api/Tuition/{id}", tuitionId)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(TuitionDto.class);
        } catch (Exception e) {
            log.error("Lỗi khi gọi GET /api/Tuition/{}: {}", tuitionId, e.getMessage());
            throw new ResourceNotFoundException("Không tìm thấy khoản học phí với ID: " + tuitionId + " bên Student Service.");
        }
    }

    public enum PaymentResult { APPLIED, REJECTED, UNKNOWN }

    /** Only explicit business rejection permits compensation; network/5xx/auth failures are uncertain. */
    public PaymentResult markTuitionAsPaid(Long tuitionId, Long transactionId, String studentId, Long amount, Integer version) {
        if (mockEnabled) return tuitionId == 888L ? PaymentResult.REJECTED : PaymentResult.APPLIED;
        var body = new LinkedHashMap<String, Object>();
        body.put("transaction_ID", transactionId);
        body.put("student_ID", studentId);
        body.put("amount", amount);
        if (version != null) body.put("expected_Version", version);
        try {
            var response = restClient.post().uri("/api/Tuition/{id}/pay", tuitionId)
                    .header("X-Internal-API-Key", internalApiKey).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toBodilessEntity();
            return response.getStatusCode().is2xxSuccessful() ? PaymentResult.APPLIED : PaymentResult.UNKNOWN;
        } catch (HttpClientErrorException ex) {
            int status = ex.getStatusCode().value();
            return status == 400 || status == 404 || status == 409 || status == 422
                    ? PaymentResult.REJECTED : PaymentResult.UNKNOWN;
        } catch (RestClientException ex) {
            return PaymentResult.UNKNOWN;
        }
    }
}
