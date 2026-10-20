package vn.edu.tdtu.ibanking.paymentservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.edu.tdtu.ibanking.paymentservice.client.UserServiceClient;

@Component
@RequiredArgsConstructor
public class PaymentAuthenticationFilter extends OncePerRequestFilter {
    private final UserServiceClient userClient;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "/health".equals(request.getRequestURI()) || "OPTIONS".equals(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)
                || authorization.substring(7).isBlank()) {
            reject(response, 401); return;
        }
        PaymentActor actor;
        try {
            actor = userClient.getAuthenticatedUser(authorization);
        } catch (HttpClientErrorException ex) {
            reject(response, ex.getStatusCode().value() == 401 || ex.getStatusCode().value() == 403 ? 401 : 503); return;
        } catch (RestClientException ex) {
            reject(response, 503); return;
        }
        if (actor == null || actor.userId() == null || actor.userId() <= 0 || actor.role() == null) {
            reject(response, 503); return;
        }
        request.setAttribute("paymentActor", actor);
        chain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, int status) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        if (status == 401) response.setHeader("WWW-Authenticate", "Bearer");
        response.getWriter().write(status == 401 ? "{\"error\":\"Unauthorized\"}" : "{\"error\":\"Authentication service unavailable\"}");
    }
}
