package heeyoung.tabletap.tenant;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class RestaurantController {

    public record RestaurantResponse(String name, String slug, String currency) {
    }

    private final TenantRepository tenants;

    public RestaurantController(TenantRepository tenants) {
        this.tenants = tenants;
    }

    @GetMapping("/api/admin/restaurant")
    RestaurantResponse current() {
        return TenantContext.current()
                .flatMap(tenants::findById)
                .map(tenant -> new RestaurantResponse(tenant.getName(), tenant.getSlug(), tenant.getCurrency()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Restaurant not found"));
    }
}
