package heeyoung.tabletap.order;

public class IllegalOrderTransitionException extends RuntimeException {

    public IllegalOrderTransitionException(OrderStatus from, OrderStatus to) {
        super("An order that is " + from + " cannot become " + to);
    }
}
