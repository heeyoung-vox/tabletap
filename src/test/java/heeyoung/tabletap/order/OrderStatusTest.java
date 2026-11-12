package heeyoung.tabletap.order;

import static heeyoung.tabletap.order.OrderStatus.ACCEPTED;
import static heeyoung.tabletap.order.OrderStatus.CANCELLED;
import static heeyoung.tabletap.order.OrderStatus.PAID;
import static heeyoung.tabletap.order.OrderStatus.PLACED;
import static heeyoung.tabletap.order.OrderStatus.PREPARING;
import static heeyoung.tabletap.order.OrderStatus.SERVED;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class OrderStatusTest {

    // The diagram in plan.md, written out by hand so the test does not just
    // repeat the code it checks.
    static final Map<OrderStatus, Set<OrderStatus>> LEGAL = Map.of(
            PLACED, Set.of(ACCEPTED, CANCELLED),
            ACCEPTED, Set.of(PREPARING, CANCELLED),
            PREPARING, Set.of(SERVED),
            SERVED, Set.of(PAID),
            PAID, Set.of(),
            CANCELLED, Set.of());

    static Stream<Arguments> everyPair() {
        return Stream.of(OrderStatus.values()).flatMap(from ->
                Stream.of(OrderStatus.values()).map(to -> Arguments.of(from, to)));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("everyPair")
    void onlyTheDiagramsTransitionsAreAllowed(OrderStatus from, OrderStatus to) {
        assertThat(from.canMoveTo(to)).isEqualTo(LEGAL.get(from).contains(to));
    }
}
