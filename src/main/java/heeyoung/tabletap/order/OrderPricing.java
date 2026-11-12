package heeyoung.tabletap.order;

import heeyoung.tabletap.menu.CustomerMenu;
import heeyoung.tabletap.order.OrderDtos.OrderLine;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Checks an order against the menu and prices it. Prices sent by a client are never used. */
public final class OrderPricing {

    static final int MAX_QUANTITY = 99;

    public record PricedOption(String groupName, String optionName, long priceDelta) {
    }

    public record PricedLine(
            UUID menuItemId, String itemName, long unitPrice, int quantity, long lineTotal, String note,
            List<PricedOption> options) {
    }

    public record PricedOrder(List<PricedLine> lines, long total) {
    }

    private OrderPricing() {
    }

    public static PricedOrder price(CustomerMenu menu, List<OrderLine> lines) {
        Map<UUID, CustomerMenu.Item> items = menu.items().stream()
                .collect(Collectors.toMap(CustomerMenu.Item::id, Function.identity()));
        List<PricedLine> priced = new ArrayList<>();
        long total = 0;
        for (OrderLine line : lines) {
            CustomerMenu.Item item = items.get(line.menuItemId());
            if (item == null) {
                throw new InvalidOrderException("An item in your order is no longer available");
            }
            PricedLine pricedLine = priceLine(item, line);
            priced.add(pricedLine);
            total = Math.addExact(total, pricedLine.lineTotal());
        }
        return new PricedOrder(priced, total);
    }

    private static PricedLine priceLine(CustomerMenu.Item item, OrderLine line) {
        if (line.quantity() < 1 || line.quantity() > MAX_QUANTITY) {
            throw new InvalidOrderException("Quantity of " + item.name() + " must be between 1 and " + MAX_QUANTITY);
        }
        Set<UUID> chosen = new HashSet<>(line.optionIds());
        if (chosen.size() != line.optionIds().size()) {
            throw new InvalidOrderException("An option was chosen twice for " + item.name());
        }

        List<PricedOption> options = new ArrayList<>();
        long unitPrice = item.basePrice();
        for (CustomerMenu.OptionGroup group : item.optionGroups()) {
            List<CustomerMenu.Option> picked = group.options().stream()
                    .filter(option -> chosen.contains(option.id()))
                    .toList();
            if (picked.size() < group.minSelect()) {
                throw new InvalidOrderException(
                        "Choose at least " + group.minSelect() + " " + group.name() + " for " + item.name());
            }
            if (picked.size() > group.maxSelect()) {
                throw new InvalidOrderException(
                        "Choose at most " + group.maxSelect() + " " + group.name() + " for " + item.name());
            }
            for (CustomerMenu.Option option : picked) {
                options.add(new PricedOption(group.name(), option.name(), option.priceDelta()));
                unitPrice = Math.addExact(unitPrice, option.priceDelta());
            }
        }
        if (options.size() != chosen.size()) {
            throw new InvalidOrderException("An option chosen for " + item.name() + " is not available");
        }

        return new PricedLine(item.id(), item.name(), unitPrice, line.quantity(),
                Math.multiplyExact(unitPrice, line.quantity()), note(line.note()), options);
    }

    static String note(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
