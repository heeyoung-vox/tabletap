package heeyoung.tabletap.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import heeyoung.tabletap.support.IntegrationTest;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class AuthFlowTest extends IntegrationTest {

    @Autowired
    JwtEncoder realEncoder;

    @Test
    void registeringLogsTheOwnerIn() {
        Account account = registerRestaurant("Pho Corner");

        var me = get("/api/admin/me", account.cookie());

        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody().get("email").asText()).isEqualTo(account.email());
        assertThat(me.getBody().get("role").asText()).isEqualTo("OWNER");
        assertThat(me.getBody().get("tenantId").asText()).isEqualTo(account.tenantId().toString());
    }

    @Test
    void loginCookieIsHttpOnlyAndSameSiteStrict() {
        Account account = registerRestaurant("Pho Corner");

        var login = post("/api/auth/login", Map.of("email", account.email(), "password", PASSWORD), null);

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(setCookieHeader(login)).contains("HttpOnly").contains("SameSite=Strict");
        assertThat(get("/api/admin/me", sessionCookie(login)).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void responsesNeverContainThePasswordHash() {
        Account account = registerRestaurant("Pho Corner");

        var me = get("/api/admin/me", account.cookie());

        assertThat(me.getBody().toString()).doesNotContainIgnoringCase("password");
    }

    @Test
    void emailIsCaseInsensitiveAtLogin() {
        Account account = registerRestaurant("Pho Corner");

        var login = post("/api/auth/login",
                Map.of("email", account.email().toUpperCase(), "password", PASSWORD), null);

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void wrongPasswordIsRejected() {
        Account account = registerRestaurant("Pho Corner");

        var login = post("/api/auth/login", Map.of("email", account.email(), "password", "not-the-password"), null);

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(setCookieHeader(login)).isNull();
    }

    @Test
    void unknownEmailIsRejectedTheSameWayAsAWrongPassword() {
        var login = post("/api/auth/login", Map.of("email", uniqueEmail(), "password", PASSWORD), null);

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(login.getBody().get("detail").asText()).isEqualTo("Invalid email or password");
    }

    @Test
    void duplicateEmailIsAConflict() {
        Account account = registerRestaurant("Pho Corner");

        var second = post("/api/auth/register",
                Map.of("restaurantName", "Another Place", "email", account.email(), "password", PASSWORD), null);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void restaurantsWithTheSameNameCanBothRegister() {
        String name = "Same Name " + java.util.UUID.randomUUID();

        Account first = registerRestaurant(name);
        Account second = registerRestaurant(name);

        assertThat(first.tenantId()).isNotEqualTo(second.tenantId());
    }

    @Test
    void invalidRegistrationIsABadRequest() {
        var response = post("/api/auth/register",
                Map.of("restaurantName", "", "email", "not-an-email", "password", "short"), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void adminApiRequiresLogin() {
        assertThat(get("/api/admin/me", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void malformedTokenIsRejected() {
        assertThat(meWithToken("not.a.token")).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void validlySignedTokenIsAccepted() {
        Account account = registerRestaurant("Pho Corner");

        assertThat(meWithToken(sign(realEncoder, account, Instant.now().plusSeconds(60))))
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        Account account = registerRestaurant("Pho Corner");
        var otherKey = new SecretKeySpec("a-different-secret-of-32-bytes-or-more".getBytes(), "HmacSHA256");
        var attackerEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(otherKey));

        assertThat(meWithToken(sign(attackerEncoder, account, Instant.now().plusSeconds(60))))
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void expiredTokenIsRejected() {
        Account account = registerRestaurant("Pho Corner");

        assertThat(meWithToken(sign(realEncoder, account, Instant.now().minusSeconds(600))))
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void unsignedTokenIsRejected() {
        Account account = registerRestaurant("Pho Corner");
        var encoder = Base64.getUrlEncoder().withoutPadding();
        String header = encoder.encodeToString("{\"alg\":\"none\"}".getBytes());
        String payload = encoder.encodeToString(("{\"sub\":\"" + account.userId() + "\",\"tenantId\":\""
                + account.tenantId() + "\",\"role\":\"OWNER\",\"exp\":" + Instant.now().plusSeconds(60).getEpochSecond()
                + "}").getBytes());

        assertThat(meWithToken(header + "." + payload + ".")).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void passwordLongerThanBcryptAllowsIsABadRequestAtRegistration() {
        String seventyThreeBytes = "ở".repeat(24) + "a";

        var response = post("/api/auth/register",
                Map.of("restaurantName", "Pho Corner", "email", uniqueEmail(), "password", seventyThreeBytes), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void overlongPasswordAtLoginIsRejectedNotAnError() {
        Account account = registerRestaurant("Pho Corner");

        var login = post("/api/auth/login", Map.of("email", account.email(), "password", "x".repeat(500)), null);

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private HttpStatusCode meWithToken(String token) {
        return get("/api/admin/me", TokenService.COOKIE_NAME + "=" + token).getStatusCode();
    }

    private static String sign(JwtEncoder encoder, Account account, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(account.userId().toString())
                .issuedAt(expiresAt.minusSeconds(3600))
                .expiresAt(expiresAt)
                .claim("tenantId", account.tenantId().toString())
                .claim("role", "OWNER")
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    @Test
    void staleCookieDoesNotBlockLoggingInAgain() {
        Account account = registerRestaurant("Pho Corner");

        var login = post("/api/auth/login",
                Map.of("email", account.email(), "password", PASSWORD),
                TokenService.COOKIE_NAME + "=not.a.token");

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void logoutExpiresTheCookie() {
        var logout = post("/api/auth/logout", null, null);

        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(setCookieHeader(logout)).startsWith(TokenService.COOKIE_NAME + "=;").contains("Max-Age=0");
    }
}
