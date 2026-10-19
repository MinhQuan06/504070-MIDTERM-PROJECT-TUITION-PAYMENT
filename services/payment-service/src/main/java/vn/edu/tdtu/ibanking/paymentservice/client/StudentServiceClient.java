package vn.edu.tdtu.ibanking.paymentservice.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import vn.edu.tdtu.ibanking.paymentservice.dto.TuitionDto;
import vn.edu.tdtu.ibanking.paymentservice.exception.ResourceNotFoundException;

import java.util.Map;

@Component
@Slf4j
public class StudentServiceClient {

    private final RestClient restClient;
    private final boolean mockEnabled;

    public StudentServiceClient(
            RestClient.Builder builder,
            @Value("${app.external-services.student-service-url:http://localhost:5071}") String baseUrl,
            @Value("${app.external-services.student-mock-enabled:false}") boolean mockEnabled) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.mockEnabled = mockEnabled;
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

    /**
     * Gọi POST http://localhost:5071/api/Tuition/{id}/pay
     * Body gửi lên: { "transaction_ID": <mã giao dịch> }
     */
    public boolean markTuitionAsPaid(Long tuitionId, Long transactionId) {
        if (mockEnabled) {
            if (tuitionId == 888L) {
                log.warn("[MOCK] Giả lập xung đột: Học phí tuitionId=888 vừa được người khác thanh toán!");
                return false;
            }
            log.info("[MOCK] Đã đánh dấu PAID bên Student Service cho tuitionId={}, transaction_ID={}", tuitionId, transactionId);
            return true;
        }

        try {
            log.info("Đang gọi Student Service: POST /api/Tuition/{}/pay với transaction_ID={}", tuitionId, transactionId);
            restClient.post()
                    .uri("/api/Tuition/{id}/pay", tuitionId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("transaction_ID", transactionId))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.error("Student Service từ chối gạch nợ cho tuitionId={}, transaction_ID={}: {}",
                    tuitionId, transactionId, e.getMessage());
            return false;
        }
    }
}