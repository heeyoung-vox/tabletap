package heeyoung.tabletap.order;

/** The order breaks a menu rule. The message is shown to the customer. */
public class InvalidOrderException extends RuntimeException {

    public InvalidOrderException(String message) {
        super(message);
    }
}
