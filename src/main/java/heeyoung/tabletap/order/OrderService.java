package heeyoung.tabletap.order;

import heeyoung.tabletap.floor.TableLookup.ScannedTable;
import heeyoung.tabletap.menu.MenuService;
import heeyoung.tabletap.order.OrderDtos.OrderResponse;
import heeyoung.tabletap.order.OrderDtos.PlaceOrderRequest;
import heeyoung.tabletap.tenant.Tenant;
import heeyoung.tabletap.tenant.TenantContext;
import heeyoung.tabletap.tenant.TenantRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class OrderService {

    static final Limit LIST_LIMIT = Limit.of(200);

    private final CustomerOrderRepository orders;
    private final MenuService menu;
    private final TenantRepository tenants;
    private final ApplicationEventPublisher events;

    public OrderService(
            CustomerOrderRepository orders, MenuService menu, TenantRepository tenants,
            ApplicationEventPublisher events) {
        this.orders = orders;
        this.menu = menu;
        this.tenants = tenants;
        this.events = events;
    }

    public OrderResponse place(ScannedTable table, PlaceOrderRequest request) {
        var priced = OrderPricing.price(menu.customerMenu(), request.items());
        var order = new CustomerOrder(table.tableId(), table.tableLabel(), OrderPricing.note(request.note()), priced);
        return published(orders.save(order));
    }

    @Transactional(readOnly = true)
    public Optional<OrderResponse> findByPublicToken(UUID publicToken) {
        return orders.findByPublicToken(publicToken).map(order -> response(order, currentTenant()));
    }

    // Oldest first, so the kitchen works through orders in the order they came in.
    @Transactional(readOnly = true)
    public List<OrderResponse> list(Collection<OrderStatus> statuses) {
        Sort oldestFirst = Sort.by("createdAt");
        List<CustomerOrder> found = statuses.isEmpty()
                ? orders.findBy(oldestFirst, LIST_LIMIT)
                : orders.findByStatusIn(statuses, oldestFirst, LIST_LIMIT);
        Tenant tenant = currentTenant();
        return found.stream().map(order -> response(order, tenant)).toList();
    }

    public OrderResponse transition(UUID id, OrderStatus target) {
        CustomerOrder order = orders.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        order.moveTo(target);
        return published(order);
    }

    private OrderResponse published(CustomerOrder order) {
        Tenant tenant = currentTenant();
        OrderResponse response = response(order, tenant);
        events.publishEvent(new OrderChanged(tenant.getId(), response));
        return response;
    }

    private Tenant currentTenant() {
        return TenantContext.current().flatMap(tenants::findById).orElseThrow();
    }

    private static OrderResponse response(CustomerOrder order, Tenant tenant) {
        return OrderResponse.from(order, tenant.getName(), tenant.getCurrency());
    }
}
