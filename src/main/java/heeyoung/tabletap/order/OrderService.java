package heeyoung.tabletap.order;

import heeyoung.tabletap.floor.TableLookup.ScannedTable;
import heeyoung.tabletap.menu.MenuService;
import heeyoung.tabletap.order.OrderDtos.OrderResponse;
import heeyoung.tabletap.order.OrderDtos.PlaceOrderRequest;
import heeyoung.tabletap.tenant.Tenant;
import heeyoung.tabletap.tenant.TenantContext;
import heeyoung.tabletap.tenant.TenantRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OrderService {

    private final CustomerOrderRepository orders;
    private final MenuService menu;
    private final TenantRepository tenants;

    public OrderService(CustomerOrderRepository orders, MenuService menu, TenantRepository tenants) {
        this.orders = orders;
        this.menu = menu;
        this.tenants = tenants;
    }

    public OrderResponse place(ScannedTable table, PlaceOrderRequest request) {
        var priced = OrderPricing.price(menu.customerMenu(), request.items());
        var order = new CustomerOrder(table.tableId(), table.tableLabel(), OrderPricing.note(request.note()), priced);
        return response(orders.save(order));
    }

    @Transactional(readOnly = true)
    public Optional<OrderResponse> findByPublicToken(UUID publicToken) {
        return orders.findByPublicToken(publicToken).map(this::response);
    }

    private OrderResponse response(CustomerOrder order) {
        Tenant tenant = TenantContext.current().flatMap(tenants::findById).orElseThrow();
        return OrderResponse.from(order, tenant.getName(), tenant.getCurrency());
    }
}
