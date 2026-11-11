package heeyoung.tabletap.auth;

import heeyoung.tabletap.auth.AuthDtos.LoginRequest;
import heeyoung.tabletap.auth.AuthDtos.RegisterRequest;
import heeyoung.tabletap.auth.AuthDtos.StaffResponse;
import heeyoung.tabletap.tenant.TenantContext;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private static final int MAX_REGISTER_ATTEMPTS = 3;

    private final AccountCreator accountCreator;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final String dummyHash;

    public AuthService(AccountCreator accountCreator, JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
        this.accountCreator = accountCreator;
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public StaffResponse register(RegisterRequest request) {
        String email = normalize(request.email());
        String passwordHash = passwordEncoder.encode(request.password());
        UUID tenantId = UUID.randomUUID();
        // Two restaurants registering the same name at once can both pick the same
        // slug. The loser retries and picks the next free one.
        for (int attempt = 1; ; attempt++) {
            try {
                return TenantContext.callAs(tenantId, () -> accountCreator.createTenantWithOwner(
                        tenantId, request.restaurantName().strip(), email, passwordHash));
            } catch (DataIntegrityViolationException e) {
                if (emailExists(email)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
                }
                if (attempt == MAX_REGISTER_ATTEMPTS) {
                    throw e;
                }
            }
        }
    }

    // The tenant is unknown until the user is found, so this lookup cannot go
    // through the tenant-filtered StaffUser entity.
    public StaffResponse login(LoginRequest request) {
        List<Credentials> found = jdbc.query(
                "select id, tenant_id, email, password_hash, role from staff_user where email = ?",
                (rs, row) -> new Credentials(
                        new StaffResponse(
                                rs.getObject("id", UUID.class),
                                rs.getString("email"),
                                StaffRole.valueOf(rs.getString("role")),
                                rs.getObject("tenant_id", UUID.class)),
                        rs.getString("password_hash")),
                normalize(request.email()));

        // Hash even when the email is unknown so response time does not reveal
        // which emails are registered.
        String hash = found.isEmpty() ? dummyHash : found.get(0).passwordHash();
        boolean matches = AuthDtos.fitsBcrypt(request.password())
                && passwordEncoder.matches(request.password(), hash);
        if (found.isEmpty() || !matches) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        return found.get(0).user();
    }

    private boolean emailExists(String email) {
        Boolean exists = jdbc.queryForObject(
                "select exists (select 1 from staff_user where email = ?)", Boolean.class, email);
        return Boolean.TRUE.equals(exists);
    }

    private static String normalize(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private record Credentials(StaffResponse user, String passwordHash) {
    }
}
