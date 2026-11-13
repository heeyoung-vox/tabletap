package heeyoung.tabletap.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(DatabaseAvailableCondition.class)
public abstract class IntegrationTest {

    protected static final String PASSWORD = "correct-horse-battery";

    @Autowired
    protected TestRestTemplate rest;

    @DynamicPropertySource
    static void externalDatabase(DynamicPropertyRegistry registry) {
        String url = DatabaseAvailableCondition.externalUrl();
        if (url != null) {
            registry.add("spring.datasource.url", () -> url);
        }
    }

    protected record Account(String email, String cookie, UUID userId, UUID tenantId) {
    }

    // The database outlives a single test, so every test creates its own uniquely named data.
    protected static String uniqueEmail() {
        return "owner-" + UUID.randomUUID() + "@example.test";
    }

    protected Account registerRestaurant(String restaurantName) {
        String email = uniqueEmail();
        var response = post("/api/auth/register",
                Map.of("restaurantName", restaurantName, "email", email, "password", PASSWORD), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode body = response.getBody();
        return new Account(
                email,
                sessionCookie(response),
                UUID.fromString(body.get("id").asText()),
                UUID.fromString(body.get("tenantId").asText()));
    }

    protected ResponseEntity<JsonNode> get(String path, String cookie) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers(cookie)), JsonNode.class);
    }

    protected ResponseEntity<JsonNode> post(String path, Object body, String cookie) {
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers(cookie)), JsonNode.class);
    }

    protected ResponseEntity<String> getPage(String path, String cookie) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers(cookie)), String.class);
    }

    protected ResponseEntity<byte[]> getBytes(String path, String cookie) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers(cookie)), byte[].class);
    }

    protected ResponseEntity<JsonNode> put(String path, Object body, String cookie) {
        return rest.exchange(path, HttpMethod.PUT, new HttpEntity<>(body, headers(cookie)), JsonNode.class);
    }

    protected ResponseEntity<JsonNode> delete(String path, String cookie) {
        return rest.exchange(path, HttpMethod.DELETE, new HttpEntity<>(headers(cookie)), JsonNode.class);
    }

    protected static String setCookieHeader(ResponseEntity<?> response) {
        return response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
    }

    protected static String sessionCookie(ResponseEntity<?> response) {
        String setCookie = setCookieHeader(response);
        assertThat(setCookie).isNotNull();
        return setCookie.substring(0, setCookie.indexOf(';'));
    }

    private static HttpHeaders headers(String cookie) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (cookie != null) {
            headers.add(HttpHeaders.COOKIE, cookie);
        }
        return headers;
    }
}
