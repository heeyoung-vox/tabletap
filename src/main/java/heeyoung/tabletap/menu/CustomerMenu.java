package heeyoung.tabletap.menu;

import java.util.List;
import java.util.UUID;

/**
 * What a customer can order right now: available items and available options
 * only. The public menu endpoint returns it, and order pricing validates
 * against the same object, so a customer can never order something the menu
 * did not offer.
 */
public record CustomerMenu(List<Category> categories) {

    public record Category(UUID id, String name, List<Item> items) {
    }

    public record Item(
            UUID id, String name, String description, long basePrice, String imageUrl,
            List<OptionGroup> optionGroups) {
    }

    public record OptionGroup(UUID id, String name, int minSelect, int maxSelect, List<Option> options) {
    }

    public record Option(UUID id, String name, long priceDelta) {
    }

    public List<Item> items() {
        return categories.stream().flatMap(category -> category.items().stream()).toList();
    }
}
