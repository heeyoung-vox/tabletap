package heeyoung.tabletap;

import static org.assertj.core.api.Assertions.assertThat;

import heeyoung.tabletap.support.IntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

class ApplicationSmokeTest extends IntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void healthEndpointReportsUp() {
        var response = rest.getForEntity("/actuator/health", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "UP");
    }

    @Test
    void migrationsCreateTenantTable() {
        Integer rows = jdbc.queryForObject("select count(*) from tenant", Integer.class);

        assertThat(rows).isZero();
    }
}
