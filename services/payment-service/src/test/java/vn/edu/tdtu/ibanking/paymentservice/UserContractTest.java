package vn.edu.tdtu.ibanking.paymentservice;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import vn.edu.tdtu.ibanking.paymentservice.client.UserServiceClient;
import static org.junit.jupiter.api.Assertions.*;

class UserContractTest {
    @Test
    void callsInternalEndpointWithKeyAndReadsSnakeCaseContact() throws Exception {
        var receivedKey = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/users/42", exchange -> {
            receivedKey.set(exchange.getRequestHeaders().getFirst("X-Internal-API-Key"));
            var body = "{\"user_id\":42,\"full_name\":\"Demo User\",\"email\":\"demo@example.com\",\"phone\":\"0901234567\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            var client = new UserServiceClient(RestClient.builder(), "http://127.0.0.1:" + server.getAddress().getPort(), false, "test-internal-key");
            var user = client.getUserById(42L);
            assertEquals("test-internal-key", receivedKey.get());
            assertEquals(42L, user.getUserId());
            assertEquals("Demo User", user.getFullName());
            assertEquals("demo@example.com", user.getEmail());
            assertEquals("0901234567", user.getPhone());
        } finally { server.stop(0); }
    }
}
