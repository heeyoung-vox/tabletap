package heeyoung.tabletap.order;

import heeyoung.tabletap.order.OrderDtos.OrderResponse;
import java.util.UUID;

/** Published when an order is placed or changes status. */
public record OrderChanged(UUID tenantId, OrderResponse order) {
}
