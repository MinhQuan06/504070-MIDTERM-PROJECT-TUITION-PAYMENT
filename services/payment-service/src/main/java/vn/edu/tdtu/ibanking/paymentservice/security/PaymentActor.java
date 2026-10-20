package vn.edu.tdtu.ibanking.paymentservice.security;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentActor(@JsonProperty("user_id") Long userId, String role) {
    public void requireOwner(Long ownerId) {
        if (!userId.equals(ownerId)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the owner may access this resource");
    }
    public void requireAdmin() {
        if (!"admin".equalsIgnoreCase(role)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role required");
    }
}
