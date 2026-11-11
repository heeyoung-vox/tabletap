package heeyoung.tabletap.auth;

import heeyoung.tabletap.auth.AuthDtos.StaffResponse;
import heeyoung.tabletap.tenant.Slugs;
import heeyoung.tabletap.tenant.Tenant;
import heeyoung.tabletap.tenant.TenantRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Separate from AuthService because the tenant context must be set before the
// transaction opens: Hibernate reads the tenant once, when it creates the session.
@Service
class AccountCreator {

    private final TenantRepository tenants;
    private final StaffUserRepository staffUsers;

    AccountCreator(TenantRepository tenants, StaffUserRepository staffUsers) {
        this.tenants = tenants;
        this.staffUsers = staffUsers;
    }

    @Transactional
    StaffResponse createTenantWithOwner(UUID tenantId, String restaurantName, String email, String passwordHash) {
        tenants.saveAndFlush(new Tenant(tenantId, restaurantName, uniqueSlug(restaurantName)));
        StaffUser owner = staffUsers.saveAndFlush(new StaffUser(email, passwordHash, StaffRole.OWNER));
        return StaffResponse.from(owner);
    }

    private String uniqueSlug(String restaurantName) {
        String base = Slugs.slugify(restaurantName);
        String candidate = base;
        for (int suffix = 2; tenants.existsBySlug(candidate); suffix++) {
            candidate = base + "-" + suffix;
        }
        return candidate;
    }
}
