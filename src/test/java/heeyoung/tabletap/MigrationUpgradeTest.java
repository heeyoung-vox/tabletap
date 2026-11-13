package heeyoung.tabletap;

import static org.assertj.core.api.Assertions.assertThat;

import heeyoung.tabletap.support.IntegrationTest;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** A deploy migrates a database that already holds orders, not an empty one. */
class MigrationUpgradeTest extends IntegrationTest {

    @Autowired
    DataSource dataSource;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void ordersPlacedBeforeTheVersionColumnSurviveTheUpgrade() {
        String schema = "upgrade_" + UUID.randomUUID().toString().replace("-", "");
        try {
            migrate(schema, "5");
            UUID tenant = UUID.randomUUID();
            UUID order = UUID.randomUUID();
            jdbc.update("insert into " + schema + ".tenant (id, name, slug) values (?, 'Old Cafe', ?)",
                    tenant, "old-cafe-" + tenant);
            jdbc.update("insert into " + schema + ".orders (id, tenant_id, table_label, public_token, status, total)"
                    + " values (?, ?, 'T1', ?, 'PREPARING', 75000)", order, tenant, UUID.randomUUID());
            jdbc.update("insert into " + schema + ".order_item"
                    + " (id, tenant_id, order_id, position, item_name, unit_price, quantity, line_total)"
                    + " values (?, ?, ?, 0, 'Pho Bo', 75000, 1, 75000)", UUID.randomUUID(), tenant, order);

            migrate(schema, "latest");

            var row = jdbc.queryForMap("select status, total, version from " + schema + ".orders where id = ?", order);
            assertThat(row).containsEntry("status", "PREPARING").containsEntry("total", 75000L)
                    .containsEntry("version", 0L);
            assertThat(jdbc.queryForObject(
                    "select count(*) from " + schema + ".order_item where order_id = ?", Integer.class, order))
                    .isEqualTo(1);
        } finally {
            jdbc.execute("drop schema if exists " + schema + " cascade");
        }
    }

    private void migrate(String schema, String target) {
        Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .locations("classpath:db/migration")
                .target(target)
                .load()
                .migrate();
    }
}
