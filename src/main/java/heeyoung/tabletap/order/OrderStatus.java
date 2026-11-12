package heeyoung.tabletap.order;

import java.util.EnumSet;
import java.util.Set;

public enum OrderStatus {
    PLACED,
    ACCEPTED,
    PREPARING,
    SERVED,
    PAID,
    CANCELLED;

    public Set<OrderStatus> next() {
        return switch (this) {
            case PLACED -> EnumSet.of(ACCEPTED, CANCELLED);
            case ACCEPTED -> EnumSet.of(PREPARING, CANCELLED);
            case PREPARING -> EnumSet.of(SERVED);
            case SERVED -> EnumSet.of(PAID);
            case PAID, CANCELLED -> EnumSet.noneOf(OrderStatus.class);
        };
    }

    public boolean canMoveTo(OrderStatus target) {
        return next().contains(target);
    }
}
