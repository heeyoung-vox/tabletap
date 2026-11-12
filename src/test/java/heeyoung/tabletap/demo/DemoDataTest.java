package heeyoung.tabletap.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import heeyoung.tabletap.support.IntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles({"dev", "demo"})
class DemoDataTest extends IntegrationTest {

    @Autowired
    DemoData demoData;

    @Test
    void demoOwnerCanLogInAndFindAMenuTablesAndOrders() {
        String cookie = login("owner@pho.demo");

        assertThat(get("/api/admin/items", cookie).getBody()).hasSize(6);
        assertThat(get("/api/admin/tables", cookie).getBody()).hasSize(6);
        JsonNode orders = get("/api/admin/orders", cookie).getBody();
        assertThat(orders).extracting(o -> o.get("status").asText()).contains("PLACED", "PREPARING", "SERVED");
    }

    @Test
    void secondRestaurantIsSeparate() {
        JsonNode items = get("/api/admin/items", login("owner@banhmi.demo")).getBody();

        assertThat(items).extracting(i -> i.get("name").asText()).contains("Bánh Mì Thịt").doesNotContain("Phở Gà");
    }

    @Test
    void runningAgainCreatesNothingNew() {
        String cookie = login("owner@pho.demo");
        int orders = get("/api/admin/orders", cookie).getBody().size();

        demoData.run(null);

        assertThat(get("/api/admin/orders", cookie).getBody()).hasSize(orders);
        assertThat(get("/api/admin/items", cookie).getBody()).hasSize(6);
    }

    private String login(String email) {
        var response = post("/api/auth/login", Map.of("email", email, "password", DemoData.PASSWORD), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return sessionCookie(response);
    }
}
