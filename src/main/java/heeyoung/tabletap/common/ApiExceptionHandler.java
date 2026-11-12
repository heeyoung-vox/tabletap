package heeyoung.tabletap.common;

import heeyoung.tabletap.order.IllegalOrderTransitionException;
import heeyoung.tabletap.order.InvalidOrderException;
import java.sql.SQLException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final String UNIQUE_VIOLATION = "23505";

    // Services check for duplicates first to give a specific message. This is
    // the fallback when two requests race past that check and the database's
    // unique constraint catches the second one. Any other integrity failure is
    // a bug, so it is rethrown and surfaces as a 500.
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail conflict(DataIntegrityViolationException e) {
        if (e.getMostSpecificCause() instanceof SQLException sql && UNIQUE_VIOLATION.equals(sql.getSQLState())) {
            return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "This conflicts with existing data");
        }
        throw e;
    }

    @ExceptionHandler(InvalidOrderException.class)
    ProblemDetail invalidOrder(InvalidOrderException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    @ExceptionHandler(IllegalOrderTransitionException.class)
    ProblemDetail illegalTransition(IllegalOrderTransitionException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }
}
