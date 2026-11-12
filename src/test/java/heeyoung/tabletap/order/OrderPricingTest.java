package heeyoung.tabletap.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import heeyoung.tabletap.menu.CustomerMenu;
import heeyoung.tabletap.menu.CustomerMenu.Item;
import heeyoung.tabletap.menu.CustomerMenu.Option;
import heeyoung.tabletap.menu.CustomerMenu.OptionGroup;
import heeyoung.tabletap.order.OrderDtos.OrderLine;
import heeyoung.tabletap.order.OrderPricing.PricedOption;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderPricingTest {

    final Option regular = option("Regular", 0);
    final Option large = option("Large", 15_000);
    final Option egg = option("Egg", 5_000);
    final Option beef = option("Extra beef", 20_000);
    final Option tofu = option("Tofu", 10_000);
    final Option lemon = option("Lemon", 0);

    final Item pho = new Item(UUID.randomUUID(), "Pho Bo", null, 60_000, null, List.of(
            group("Size", 1, 1, regular, large),
            group("Extras", 0, 2, egg, beef, tofu)));
    final Item tea = new Item(UUID.randomUUID(), "Iced tea", null, 10_000, null, List.of(
            group("Add", 0, 1, lemon)));

    final CustomerMenu menu = new CustomerMenu(List.of(new CustomerMenu.Category(UUID.randomUUID(), "All", List.of(pho, tea))));

    @Test
    void pricesLinesFromTheMenu() {
        var order = OrderPricing.price(menu, List.of(
                line(pho, 2, large, egg),
                line(tea, 3)));

        var first = order.lines().get(0);
        assertThat(first.itemName()).isEqualTo("Pho Bo");
        assertThat(first.unitPrice()).isEqualTo(80_000);
        assertThat(first.lineTotal()).isEqualTo(160_000);
        assertThat(first.options()).containsExactly(
                new PricedOption("Size", "Large", 15_000), new PricedOption("Extras", "Egg", 5_000));
        assertThat(order.lines().get(1).lineTotal()).isEqualTo(30_000);
        assertThat(order.total()).isEqualTo(190_000);
    }

    @Test
    void optionsAreListedInMenuOrderNotRequestOrder() {
        var order = OrderPricing.price(menu, List.of(line(pho, 1, beef, regular, egg)));

        assertThat(order.lines().get(0).options()).extracting(PricedOption::optionName)
                .containsExactly("Regular", "Egg", "Extra beef");
    }

    @Test
    void missingRequiredOptionIsRejected() {
        assertRejected(List.of(line(pho, 1, egg)), "Choose at least 1 Size for Pho Bo");
    }

    @Test
    void tooManyOptionsInAGroupAreRejected() {
        assertRejected(List.of(line(pho, 1, regular, egg, beef, tofu)), "Choose at most 2 Extras for Pho Bo");
    }

    @Test
    void twoChoicesForASingleChoiceGroupAreRejected() {
        assertRejected(List.of(line(pho, 1, regular, large)), "Choose at most 1 Size for Pho Bo");
    }

    @Test
    void itemMissingFromTheMenuIsRejected() {
        var soldOut = new Item(UUID.randomUUID(), "Banh Mi", null, 30_000, null, List.of());

        assertRejected(List.of(line(soldOut, 1)), "An item in your order is no longer available");
    }

    @Test
    void optionOfAnotherItemIsRejected() {
        assertRejected(List.of(line(pho, 1, regular, lemon)), "An option chosen for Pho Bo is not available");
    }

    @Test
    void unknownOptionIsRejected() {
        assertRejected(List.of(line(tea, 1, option("Mint", 0))), "An option chosen for Iced tea is not available");
    }

    @Test
    void quantityBelowOneIsRejected() {
        assertRejected(List.of(line(tea, 0)), "Quantity of Iced tea must be between 1 and 99");
        assertRejected(List.of(line(tea, -2)), "Quantity of Iced tea must be between 1 and 99");
    }

    @Test
    void quantityAboveTheLimitIsRejected() {
        assertRejected(List.of(line(tea, 100)), "Quantity of Iced tea must be between 1 and 99");
    }

    @Test
    void theSameOptionTwiceIsRejected() {
        assertRejected(List.of(line(pho, 1, regular, egg, egg)), "An option was chosen twice for Pho Bo");
    }

    @Test
    void blankNotesAreDroppedAndOthersTrimmed() {
        var order = OrderPricing.price(menu, List.of(
                new OrderLine(tea.id(), 1, List.of(), "   "),
                new OrderLine(tea.id(), 1, List.of(), "  no ice ")));

        assertThat(order.lines()).extracting(OrderPricing.PricedLine::note).containsExactly(null, "no ice");
    }

    private void assertRejected(List<OrderLine> lines, String message) {
        assertThatThrownBy(() -> OrderPricing.price(menu, lines))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessage(message);
    }

    private static OrderLine line(Item item, int quantity, Option... options) {
        return new OrderLine(item.id(), quantity, List.of(options).stream().map(Option::id).toList(), null);
    }

    private static OptionGroup group(String name, int min, int max, Option... options) {
        return new OptionGroup(UUID.randomUUID(), name, min, max, List.of(options));
    }

    private static Option option(String name, long priceDelta) {
        return new Option(UUID.randomUUID(), name, priceDelta);
    }
}
