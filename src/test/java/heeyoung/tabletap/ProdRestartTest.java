package heeyoung.tabletap;

import static org.assertj.core.api.Assertions.assertThat;

import heeyoung.tabletap.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.JdbcConnectionDetails;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/** The example env file deploys with prod,demo, so every restart reruns Flyway and the demo seed. */
@ActiveProfiles({"prod", "demo"})
@TestPropertySource(properties = {
        "DB_URL=jdbc:postgresql://localhost:5432/tabletap",
        "DB_USERNAME=tabletap",
        "DB_PASSWORD=tabletap",
        "JWT_SECRET=" + ProdRestartTest.SECRET,
        "APP_BASE_URL=https://tabletap.example.test"})
class ProdRestartTest extends IntegrationTest {

    static final String SECRET = "f3Qm8Zr1Vx6Lk0Tn4Wb7Yc2Hd9Sg5Pj8Ua3Ee6Ri1Oo4";

    private static final String DEMO_TENANTS =
            "(select tenant_id from staff_user where email in ('owner@pho.demo', 'owner@banhmi.demo'))";

    @Autowired
    JdbcConnectionDetails database;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void restartingOnAMigratedDatabaseAddsNoDemoData() {
        List<Integer> before = demoRowCounts();
        assertThat(before).allMatch(count -> count > 0);

        var second = new SpringApplicationBuilder(TabletapApplication.class)
                .profiles("prod", "demo")
                .properties(
                        "server.port=0",
                        "DB_URL=" + database.getJdbcUrl(),
                        "DB_USERNAME=" + database.getUsername(),
                        "DB_PASSWORD=" + database.getPassword(),
                        "JWT_SECRET=" + SECRET,
                        "APP_BASE_URL=https://tabletap.example.test")
                .run();
        second.close();

        assertThat(demoRowCounts()).isEqualTo(before);
    }

    private List<Integer> demoRowCounts() {
        return List.of("staff_user", "category", "menu_item", "dining_table", "orders").stream()
                .map(table -> jdbc.queryForObject(
                        "select count(*) from " + table + " where tenant_id in " + DEMO_TENANTS, Integer.class))
                .toList();
    }
}
