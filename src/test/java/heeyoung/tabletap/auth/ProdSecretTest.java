package heeyoung.tabletap.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Loads the real application.yml and application-prod.yml, without a database. */
class ProdSecretTest {

    private static final String GOOD_SECRET = "f3Qm8Zr1Vx6Lk0Tn4Wb7Yc2Hd9Sg5Pj8Ua3Ee6Ri1Oo4";

    @EnableConfigurationProperties(JwtProperties.class)
    static class JwtConfig {
    }

    private final ApplicationContextRunner prod = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(JwtConfig.class)
            .withPropertyValues("spring.profiles.active=prod");

    @Test
    void startsWithALongSecretAndKeepsTheSecureDefaults() {
        prod.withPropertyValues("JWT_SECRET=" + GOOD_SECRET).run(context -> {
            assertThat(context).hasNotFailed();
            JwtProperties properties = context.getBean(JwtProperties.class);
            assertThat(properties.cookieSecure()).isTrue();
            assertThat(properties.ttl()).isEqualTo(Duration.ofHours(8));
        });
    }

    @Test
    void refusesToStartWithoutASecret() {
        prod.run(context -> assertThat(context).hasFailed());
    }

    @Test
    void refusesToStartWithAShortSecret() {
        prod.withPropertyValues("JWT_SECRET=only-thirty-one-bytes-long-0123").run(context ->
                assertThat(context).getFailure().rootCause().hasMessageContaining("at least 32 bytes"));
    }

    @Test
    void refusesToStartWithThePlaceholderSecretFromTheExampleFile() throws IOException {
        String placeholder = Files.readAllLines(Path.of("deploy/tabletap.env.example")).stream()
                .filter(line -> line.startsWith("JWT_SECRET="))
                .map(line -> line.substring("JWT_SECRET=".length()))
                .findFirst().orElseThrow();

        prod.withPropertyValues("JWT_SECRET=" + placeholder).run(context -> assertThat(context).hasFailed());
    }
}
