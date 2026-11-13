package heeyoung.tabletap.auth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("tabletap.jwt")
public record JwtProperties(String secret, Duration ttl, boolean cookieSecure) {

    private static final int MIN_SECRET_BYTES = 32;

    // The start of the example value in deploy/tabletap.env.example. That value is public, so a
    // server running with it would accept login tokens anyone can forge.
    private static final String PLACEHOLDER_PREFIX = "change-me";

    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException("tabletap.jwt.secret must be at least 32 bytes");
        }
        if (secret.startsWith(PLACEHOLDER_PREFIX)) {
            throw new IllegalArgumentException(
                    "tabletap.jwt.secret is still the example value. Generate one with: openssl rand -base64 48");
        }
    }
}
