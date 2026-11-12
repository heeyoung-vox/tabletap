package heeyoung.tabletap.order;

import heeyoung.tabletap.menu.CustomerMenu;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class OrderDtos {

    private OrderDtos() {
    }

    // Carries no prices. The server prices every order from the current menu.
    public record PlaceOrderRequest(
            @Size(max = 500) String note,
            @NotEmpty @Size(max = 50) List<@NotNull @Valid OrderLine> items) {
    }

    public record OrderLine(
            @NotNull UUID menuItemId,
            int quantity,
            @Size(max = 30) List<@NotNull UUID> optionIds,
            @Size(max = 200) String note) {

        public OrderLine {
            optionIds = optionIds == null ? List.of() : optionIds;
        }
    }

    public record PublicMenuResponse(
            String restaurantName, String tableLabel, String currency, List<CustomerMenu.Category> categories) {
    }

    public record OrderResponse(
            UUID id,
            UUID publicToken,
            String restaurantName,
            String currency,
            String tableLabel,
            OrderStatus status,
            List<OrderStatus> nextStatuses,
            long total,
            String note,
            Instant createdAt,
            List<LineResponse> items) {

        static OrderResponse from(CustomerOrder order, String restaurantName, String currency) {
            return new OrderResponse(
                    order.getId(),
                    order.getPublicToken(),
                    restaurantName,
                    currency,
                    order.getTableLabel(),
                    order.getStatus(),
                    List.copyOf(order.getStatus().next()),
                    order.getTotal(),
                    order.getNote(),
                    order.getCreatedAt(),
                    order.getItems().stream().map(LineResponse::from).toList());
        }
    }

    public record LineResponse(
            String name, int quantity, long unitPrice, long lineTotal, String note, List<OptionResponse> options) {

        static LineResponse from(OrderItem item) {
            return new LineResponse(item.getItemName(), item.getQuantity(), item.getUnitPrice(), item.getLineTotal(),
                    item.getNote(), item.getOptions().stream().map(OptionResponse::from).toList());
        }
    }

    public record OptionResponse(String groupName, String optionName, long priceDelta) {

        static OptionResponse from(OrderItemOption option) {
            return new OptionResponse(option.getGroupName(), option.getOptionName(), option.getPriceDelta());
        }
    }
}
