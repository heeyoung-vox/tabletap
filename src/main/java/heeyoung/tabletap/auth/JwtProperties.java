package heeyoung.tabletap.auth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("tabletap.jwt")
public record JwtProperties(String secret, Duration ttl, boolean cookieSecure) {

    private static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException("tabletap.jwt.secret must be at least 32 bytes");
        }
    }
}
