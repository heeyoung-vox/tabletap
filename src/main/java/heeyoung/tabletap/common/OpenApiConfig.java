package heeyoung.tabletap.common;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpenApiConfig {

    @Bean
    OpenAPI openApi() {
        return new OpenAPI().info(new Info()
                .title("TableTap API")
                .version("1.0")
                .description("""
                        QR menu and ordering for restaurants. Admin endpoints use the login cookie set by \
                        POST /api/auth/login, so log in first in the same browser to try them here. \
                        Public endpoints take a table's QR token or an order's public token instead."""));
    }
}
