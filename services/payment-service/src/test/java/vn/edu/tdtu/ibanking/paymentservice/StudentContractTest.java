package vn.edu.tdtu.ibanking.paymentservice;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.tdtu.ibanking.paymentservice.dto.TuitionDto;
import static org.junit.jupiter.api.Assertions.*;

class StudentContractTest {
    @Test
    void readsActualStudentJsonWithoutConflictingSetters() {
        var mapper = JsonMapper.builder().build();
        var fee = mapper.readValue("""
                {"tuition_ID":7,"student_ID":"SV001","semester":"TEST","amount":15000000,
                 "status":"PAID","version":1,"paid_By_Transaction_ID":42}
                """, TuitionDto.class);
        assertEquals(7L, fee.getTuitionId());
        assertEquals("SV001", fee.getStudentId());
        assertEquals(15000000L, fee.getAmount());
        assertEquals("PAID", fee.getStatus());
        assertEquals(42L, fee.getPaidByTransactionId());
    }
}
