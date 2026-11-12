package heeyoung.tabletap.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.springframework.http.HttpStatus;

/** Integration tests that need a restaurant with a table and a menu to order from. */
public abstract class OrderingTest extends IntegrationTest {

    // A restaurant with one table "T1" and a "Noodles" category holding Pho Bo:
    // Size (Regular +0, Large +15,000, required) and Extras (Egg +5,000, sold-out Extra beef, up to 2).
    protected record Restaurant(Account owner, String qrToken, String tableId, String categoryId, JsonNode pho) {

        public String option(String name) {
            for (JsonNode group : pho.get("optionGroups")) {
                for (JsonNode option : group.get("options")) {
                    if (option.get("name").asText().equals(name)) {
                        return option.get("id").asText();
                    }
                }
            }
            throw new IllegalArgumentException(name);
        }
    }

    protected Restaurant openRestaurant(String name) {
        Account owner = registerRestaurant(name);
        JsonNode table = post("/api/admin/tables", Map.of("label", "T1"), owner.cookie()).getBody();
        String qrToken = URI.create(table.get("url").asText()).getPath().substring("/t/".length());
        var partial = new Restaurant(owner, qrToken, table.get("id").asText(), null, null);
        String categoryId = createCategory(partial, "Noodles");
        JsonNode pho = createItem(partial, categoryId, "Pho Bo", 60_000, List.of(
                group("Size", 1, 1, option("Regular", 0, true), option("Large", 15_000, true)),
                group("Extras", 0, 2, option("Egg", 5_000, true), option("Extra beef", 20_000, false))));
        return new Restaurant(owner, qrToken, partial.tableId(), categoryId, pho);
    }

    protected JsonNode placeOrder(Restaurant r) {
        var placed = post("/api/public/" + r.qrToken() + "/orders",
                Map.of("items", List.of(line(r.pho(), 1, r.option("Regular")))), null);
        assertThat(placed.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return placed.getBody();
    }

    protected String createCategory(Restaurant r, String name) {
        var response = post("/api/admin/categories", Map.of("name", name, "sortOrder", 0), r.owner().cookie());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().get("id").asText();
    }

    protected JsonNode createItem(Restaurant r, String categoryId, String name, long price, List<Map<String, Object>> groups) {
        var response = post("/api/admin/items", Map.of(
                "categoryId", categoryId, "name", name, "basePrice", price, "sortOrder", 0, "optionGroups", groups),
                r.owner().cookie());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    protected static Map<String, Object> line(JsonNode item, int quantity, String... optionIds) {
        return Map.of("menuItemId", item.get("id").asText(), "quantity", quantity, "optionIds", List.of(optionIds));
    }

    @SafeVarargs
    protected static Map<String, Object> group(String name, int min, int max, Map<String, Object>... options) {
        return Map.of("name", name, "minSelect", min, "maxSelect", max, "options", List.of(options));
    }

    protected static Map<String, Object> option(String name, long priceDelta, boolean available) {
        return Map.of("name", name, "priceDelta", priceDelta, "available", available);
    }

    protected static List<String> names(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false).map(n -> n.get("name").asText()).toList();
    }
}
