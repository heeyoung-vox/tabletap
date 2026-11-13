package heeyoung.tabletap.common;

import heeyoung.tabletap.order.IllegalOrderTransitionException;
import heeyoung.tabletap.order.InvalidOrderException;
import java.sql.SQLException;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final String UNIQUE_VIOLATION = "23505";
    private static final String FOREIGN_KEY_VIOLATION = "23503";
    private static final Set<String> LOST_RACES = Set.of(UNIQUE_VIOLATION, FOREIGN_KEY_VIOLATION);

    // Services check first to give a specific message: is the label taken, does
    // the category still have items. This is the fallback when two requests race
    // past that check and the database's unique or foreign key constraint stops
    // the second one. Any other integrity failure is a bug, so it is rethrown
    // and surfaces as a 500.
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail conflict(DataIntegrityViolationException e) {
        if (e.getMostSpecificCause() instanceof SQLException sql && LOST_RACES.contains(sql.getSQLState())) {
            return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "This conflicts with existing data");
        }
        throw e;
    }

    @ExceptionHandler(InvalidOrderException.class)
    ProblemDetail invalidOrder(InvalidOrderException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail concurrentChange(ObjectOptimisticLockingFailureException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Someone else changed this at the same time. Reload and try again.");
    }

    @ExceptionHandler(IllegalOrderTransitionException.class)
    ProblemDetail illegalTransition(IllegalOrderTransitionException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }
}
