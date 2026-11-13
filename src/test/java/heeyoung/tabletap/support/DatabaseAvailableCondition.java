package heeyoung.tabletap.support;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Integration tests need PostgreSQL. They use the database named by TEST_DATABASE_URL
 * and are skipped when it is not set, except in CI, where a missing database fails the build.
 */
public class DatabaseAvailableCondition implements ExecutionCondition {

    public static final String EXTERNAL_URL_ENV = "TEST_DATABASE_URL";

    public static String externalUrl() {
        String url = System.getenv(EXTERNAL_URL_ENV);
        return url == null || url.isBlank() ? null : url;
    }

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        if (externalUrl() != null) {
            return ConditionEvaluationResult.enabled(EXTERNAL_URL_ENV + " is set");
        }
        if (System.getenv("CI") != null) {
            throw new IllegalStateException("CI must run the integration tests, but no database is available");
        }
        return ConditionEvaluationResult.disabled(
                "No database: set " + EXTERNAL_URL_ENV);
    }
}
