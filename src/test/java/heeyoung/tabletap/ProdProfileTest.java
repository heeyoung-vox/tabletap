package heeyoung.tabletap;

import static org.assertj.core.api.Assertions.assertThat;

import heeyoung.tabletap.support.IntegrationTest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/** Runs the app with the settings that get deployed. Every other test uses the dev profile. */
@ActiveProfiles("prod")
@TestPropertySource(properties = {
        "DB_URL=jdbc:postgresql://localhost:5432/tabletap",
        "DB_USERNAME=tabletap",
        "DB_PASSWORD=tabletap",
        "JWT_SECRET=f3Qm8Zr1Vx6Lk0Tn4Wb7Yc2Hd9Sg5Pj8Ua3Ee6Ri1Oo4",
        "APP_BASE_URL=https://tabletap.example.test"})
class ProdProfileTest extends IntegrationTest {

    @Test
    void loginCookieIsSecure() {
        Account account = registerRestaurant("Pho Corner");

        var login = post("/api/auth/login", Map.of("email", account.email(), "password", PASSWORD), null);

        assertThat(setCookieHeader(login)).contains("Secure").contains("HttpOnly").contains("SameSite=Strict");
    }

    @Test
    void tableLinksUseThePublicAddress() {
        Account account = registerRestaurant("Pho Corner");

        var table = post("/api/admin/tables", Map.of("label", "T1"), account.cookie());

        assertThat(table.getBody().get("url").asText()).startsWith("https://tabletap.example.test/t/");
    }

    // Caddy terminates HTTPS and forwards plain HTTP with X-Forwarded-Proto.
    @Test
    void loginRedirectKeepsHttpsBehindTheProxy() throws Exception {
        var request = HttpRequest.newBuilder(URI.create(rest.getRootUri() + "/admin/menu"))
                .header("X-Forwarded-Proto", "https")
                .header("X-Forwarded-For", "203.0.113.7")
                .build();

        var response = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()
                .send(request, HttpResponse.BodyHandlers.discarding());

        assertThat(response.statusCode()).isEqualTo(302);
        URI location = URI.create(response.headers().firstValue("Location").orElseThrow());
        assertThat(location.getScheme()).isEqualTo("https");
        assertThat(location.getPath()).isEqualTo("/login");
    }

    @Test
    void healthShowsOnlyTheStatus() {
        var health = get("/actuator/health", null);

        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(health.getBody().fieldNames()).toIterable().containsExactly("status");
    }

    @Test
    void noOtherActuatorEndpointIsReachable() {
        for (String endpoint : List.of("env", "beans", "metrics", "info", "configprops", "heapdump", "loggers")) {
            assertThat(getPage("/actuator/" + endpoint, null).getStatusCode().is2xxSuccessful())
                    .as(endpoint).isFalse();
        }
    }
}
