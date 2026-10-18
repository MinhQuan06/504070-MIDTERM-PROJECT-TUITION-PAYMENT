package vn.edu.tdtu.ibanking.paymentservice.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TuitionDto {

    @JsonProperty("tuition_ID")
    @JsonAlias({"tuitionId", "tuition_id"})
    private Long tuitionId;

    @JsonProperty("student_ID")
    @JsonAlias({"studentId", "student_id"})
    private String studentId;

    private String semester;

    private Long amount;

    private String status; // "UNPAID" hoặc "PAID"

    private Integer version;

    @JsonProperty("paid_By_Transaction_ID")
    @JsonAlias({"paidByTransactionId", "paid_by_transaction_id"})
    private Long paidByTransactionId;

    // Bổ sung các hàm Setter trùng tên JSON của C# để tương thích tuyệt đối với cả Jackson 2 & Jackson 3
    public void setTuition_ID(Long tuition_ID) {
        this.tuitionId = tuition_ID;
    }

    public void setStudent_ID(String student_ID) {
        this.studentId = student_ID;
    }

    public void setPaid_By_Transaction_ID(Long paid_By_Transaction_ID) {
        this.paidByTransactionId = paid_By_Transaction_ID;
    }
}