package heeyoung.tabletap.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import heeyoung.tabletap.auth.StaffUser;
import heeyoung.tabletap.auth.StaffUserRepository;
import heeyoung.tabletap.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

class TenantIsolationTest extends IntegrationTest {

    @Autowired
    StaffUserRepository staffUsers;

    @Test
    void staffListShowsOnlyTheCallersTenant() {
        Account alpha = registerRestaurant("Alpha Kitchen");
        Account beta = registerRestaurant("Beta Bistro");

        var list = get("/api/admin/staff", alpha.cookie());

        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody()).hasSize(1);
        assertThat(list.getBody().get(0).get("email").asText()).isEqualTo(alpha.email());
        assertThat(list.getBody().toString()).doesNotContain(beta.email());
    }

    @Test
    void anotherTenantsStaffUserCannotBeFetchedById() {
        Account alpha = registerRestaurant("Alpha Kitchen");
        Account beta = registerRestaurant("Beta Bistro");

        assertThat(get("/api/admin/staff/" + beta.userId(), alpha.cookie()).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get("/api/admin/staff/" + beta.userId(), beta.cookie()).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void repositoryQueriesAreScopedToTheCurrentTenant() {
        Account alpha = registerRestaurant("Alpha Kitchen");
        Account beta = registerRestaurant("Beta Bistro");

        var visibleToAlpha = TenantContext.callAs(alpha.tenantId(), () -> staffUsers.findAll());

        assertThat(visibleToAlpha).extracting(StaffUser::getEmail).containsExactly(alpha.email());
        assertThat(TenantContext.callAs(alpha.tenantId(), () -> staffUsers.findById(beta.userId()))).isEmpty();
        assertThat(TenantContext.callAs(alpha.tenantId(), () -> staffUsers.count())).isEqualTo(1);
    }

    @Test
    void queriesWithoutATenantSeeNothing() {
        Account alpha = registerRestaurant("Alpha Kitchen");

        assertThat(staffUsers.findAll()).isEmpty();
        assertThat(staffUsers.findById(alpha.userId())).isEmpty();
    }

    @Test
    void tenantContextDoesNotLeakPastTheCall() {
        Account alpha = registerRestaurant("Alpha Kitchen");

        TenantContext.callAs(alpha.tenantId(), () -> staffUsers.count());

        assertThat(TenantContext.current()).isEmpty();
    }
}
