package vn.edu.tdtu.ibanking.paymentservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties={"app.outbox.enabled=false", "app.outbox.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="})
class PaymentServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
