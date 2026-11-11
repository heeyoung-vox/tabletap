package heeyoung.tabletap;

import static org.assertj.core.api.Assertions.assertThat;

import heeyoung.tabletap.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

class ApplicationSmokeTest extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void healthEndpointReportsUp() {
        var response = get("/actuator/health", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("status").asText()).isEqualTo("UP");
    }

    @Test
    void migrationsCreateTenantTable() {
        Integer rows = jdbc.queryForObject("select count(*) from tenant", Integer.class);

        assertThat(rows).isNotNegative();
    }

    @Test
    void unknownPathsAreDenied() {
        assertThat(get("/not-a-real-page", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
