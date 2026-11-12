package heeyoung.tabletap.kitchen;

import heeyoung.tabletap.order.OrderDtos.OrderResponse;
import heeyoung.tabletap.order.OrderService;
import heeyoung.tabletap.order.OrderStatus;
import heeyoung.tabletap.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/admin/orders")
public class KitchenController {

    public record TransitionRequest(@NotNull OrderStatus to) {
    }

    private final OrderService orders;
    private final OrderEvents events;

    public KitchenController(OrderService orders, OrderEvents events) {
        this.orders = orders;
        this.events = events;
    }

    @GetMapping
    List<OrderResponse> list(@RequestParam(name = "status", defaultValue = "") List<OrderStatus> statuses) {
        return orders.list(statuses);
    }

    @PostMapping("/{id}/transition")
    OrderResponse transition(@PathVariable UUID id, @Valid @RequestBody TransitionRequest request) {
        return orders.transition(id, request.to());
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter stream() {
        return events.subscribe(TenantContext.current().orElseThrow());
    }
}
