package heeyoung.tabletap.web;

import static org.assertj.core.api.Assertions.assertThat;

import heeyoung.tabletap.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ApiDocsTest extends IntegrationTest {

    @Test
    void openApiDescribesThePublicAndAdminApi() {
        var docs = getPage("/v3/api-docs", null);

        assertThat(docs.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(docs.getBody())
                .contains("TableTap API")
                .contains("/api/public/{qrToken}/orders")
                .contains("/api/admin/orders/{id}/transition");
    }

    @Test
    void swaggerUiOpensWithoutLogin() {
        var page = getPage("/swagger-ui.html", null);

        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(page.getBody()).containsIgnoringCase("swagger");
    }
}
