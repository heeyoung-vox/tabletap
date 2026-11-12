package heeyoung.tabletap.order;

import heeyoung.tabletap.floor.TableLookup;
import heeyoung.tabletap.floor.TableLookup.ScannedTable;
import heeyoung.tabletap.menu.MenuService;
import heeyoung.tabletap.order.OrderDtos.OrderResponse;
import heeyoung.tabletap.order.OrderDtos.PlaceOrderRequest;
import heeyoung.tabletap.order.OrderDtos.PublicMenuResponse;
import heeyoung.tabletap.tenant.TenantContext;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// Customers have no login. Each request names a QR token or an order's public
// token, which decides the tenant the work runs as.
@RestController
@RequestMapping("/api/public")
public class CustomerController {

    private final TableLookup tables;
    private final OrderLookup orderLookup;
    private final MenuService menu;
    private final OrderService orders;

    public CustomerController(TableLookup tables, OrderLookup orderLookup, MenuService menu, OrderService orders) {
        this.tables = tables;
        this.orderLookup = orderLookup;
        this.menu = menu;
        this.orders = orders;
    }

    @GetMapping("/{qrToken}/menu")
    PublicMenuResponse menu(@PathVariable String qrToken) {
        ScannedTable table = table(qrToken);
        return TenantContext.callAs(table.tenantId(), () -> new PublicMenuResponse(
                table.restaurantName(), table.tableLabel(), table.currency(), menu.customerMenu().categories()));
    }

    @PostMapping("/{qrToken}/orders")
    @ResponseStatus(HttpStatus.CREATED)
    OrderResponse place(@PathVariable String qrToken, @Valid @RequestBody PlaceOrderRequest request) {
        ScannedTable table = table(qrToken);
        return TenantContext.callAs(table.tenantId(), () -> orders.place(table, request));
    }

    @GetMapping("/orders/{publicToken}")
    OrderResponse order(@PathVariable String publicToken) {
        return OrderLookup.parse(publicToken)
                .flatMap(token -> orderLookup.tenantOf(token)
                        .flatMap(tenantId -> TenantContext.callAs(tenantId, () -> orders.findByPublicToken(token))))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }

    private ScannedTable table(String qrToken) {
        return tables.findActive(qrToken)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "This QR code is no longer valid"));
    }
}
