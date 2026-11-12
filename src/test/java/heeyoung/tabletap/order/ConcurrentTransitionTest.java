package heeyoung.tabletap.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import heeyoung.tabletap.support.OrderingTest;
import heeyoung.tabletap.tenant.TenantContext;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

class ConcurrentTransitionTest extends OrderingTest {

    @Autowired
    CustomerOrderRepository orders;

    @Test
    void aChangeBasedOnAStaleCopyIsRejected() {
        Restaurant r = openRestaurant("Pho Corner");
        UUID id = UUID.fromString(placeOrder(r).get("id").asText());
        UUID tenant = r.owner().tenantId();
        CustomerOrder stale = TenantContext.callAs(tenant, () -> orders.findById(id).orElseThrow());

        post("/api/admin/orders/" + id + "/transition", Map.of("to", "ACCEPTED"), r.owner().cookie());
        stale.moveTo(OrderStatus.CANCELLED);

        assertThatThrownBy(() -> TenantContext.callAs(tenant, () -> orders.save(stale)))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThat(TenantContext.callAs(tenant, () -> orders.findById(id).orElseThrow().getStatus()))
                .isEqualTo(OrderStatus.ACCEPTED);
    }
}
