package heeyoung.tabletap.order;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

// The customer following an order is not logged in, so the tenant comes from
// the order's public token. Plain SQL, because the entity is tenant-filtered.
@Service
public class OrderLookup {

    private final JdbcTemplate jdbc;

    public OrderLookup(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<UUID> tenantOf(UUID publicToken) {
        return jdbc.query(
                "select tenant_id from orders where public_token = ?",
                (rs, row) -> rs.getObject("tenant_id", UUID.class),
                publicToken).stream().findFirst();
    }

    public static Optional<UUID> parse(String token) {
        try {
            return Optional.of(UUID.fromString(token));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
