package heeyoung.tabletap.floor;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

// A customer who scans a code is not logged in, so the tenant is unknown until
// the token is resolved. That is why this lookup uses plain SQL and not the
// tenant-filtered DiningTable entity.
@Service
public class TableLookup {

    public record ScannedTable(UUID tenantId, UUID tableId, String tableLabel, String restaurantName) {
    }

    private final JdbcTemplate jdbc;

    public TableLookup(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<ScannedTable> findActive(String qrToken) {
        UUID token;
        try {
            token = UUID.fromString(qrToken);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        return jdbc.query("""
                        select t.tenant_id, t.id, t.label, r.name
                        from dining_table t
                        join tenant r on r.id = t.tenant_id
                        where t.qr_token = ? and t.active
                        """,
                (rs, row) -> new ScannedTable(
                        rs.getObject("tenant_id", UUID.class),
                        rs.getObject("id", UUID.class),
                        rs.getString("label"),
                        rs.getString("name")),
                token).stream().findFirst();
    }
}
