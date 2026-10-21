package vn.edu.tdtu.ibanking.paymentservice;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import vn.edu.tdtu.ibanking.paymentservice.dto.OtpEmailEvent;
import vn.edu.tdtu.ibanking.paymentservice.dto.PaymentSuccessEmailEvent;
import vn.edu.tdtu.ibanking.paymentservice.entity.Otp;
import vn.edu.tdtu.ibanking.paymentservice.repository.OtpRepository;
import vn.edu.tdtu.ibanking.paymentservice.service.OtpService;
import org.mockito.ArgumentCaptor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificationContractTest {
    private final JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();
    private String serialize(Object value) {
        return new String(converter.toMessage(value, new MessageProperties()).getBody(), StandardCharsets.UTF_8);
    }
    @Test
    void otpJsonMatchesConsumerContract() throws Exception {
        String json = serialize(OtpEmailEvent.builder()
                .eventId("contract-otp-1").type("OtpRequested").transactionId("9007199254740993")
                .email("student@example.com").fullName("Nguyễn Văn An").otp("012345")
                .expiresAt(Instant.parse("2030-01-01T00:05:00Z")).build());
        assertTrue(json.contains("\"transactionId\":\"9007199254740993\""));
        assertTrue(json.contains("\"expiresAt\":\"2030-01-01T00:05:00Z\""));
        assertFalse(json.contains("otpCode"));
        writeFixture("otp.json", json);
    }
    @Test
    void paymentJsonMatchesConsumerContract() throws Exception {
        String json = serialize(PaymentSuccessEmailEvent.builder()
                .eventId("payment-succeeded-42").type("PaymentSucceeded").transactionId("42")
                .email("student@example.com").fullName("Nguyễn Văn An").studentId("52300001")
                .amount(15000000L).completedAt(Instant.parse("2030-01-01T00:01:00Z")).build());
        assertTrue(json.contains("\"completedAt\":\"2030-01-01T00:01:00Z\""));
        assertTrue(json.contains("\"amount\":15000000"));
        writeFixture("payment.json", json);
    }
    @Test
    void issuedOtpUsesExactDatabaseExpiryAndFreshEventIds() {
        OtpRepository repository = mock(OtpRepository.class);
        OtpService service = new OtpService(repository, mock(vn.edu.tdtu.ibanking.paymentservice.service.OtpPolicy.class));
        OtpService.IssuedOtp first = service.generateAndSaveOtp(42L);
        OtpService.IssuedOtp second = service.generateAndSaveOtp(42L);
        ArgumentCaptor<Otp> captor = ArgumentCaptor.forClass(Otp.class);
        verify(repository, times(2)).save(captor.capture());
        assertEquals(captor.getAllValues().get(0).getExpiresAt().atZone(ZoneId.systemDefault()).toInstant(), first.expiresAt());
        assertEquals(captor.getAllValues().get(1).getExpiresAt().atZone(ZoneId.systemDefault()).toInstant(), second.expiresAt());
        assertNotEquals(first.eventId(), second.eventId());
        assertTrue(first.code().matches("[0-9]{6}"));
    }
    private void writeFixture(String name, String json) throws Exception {
        Path folder = Path.of("target", "notification-contract");
        Files.createDirectories(folder);
        Files.writeString(folder.resolve(name), json, StandardCharsets.UTF_8);
    }
}
