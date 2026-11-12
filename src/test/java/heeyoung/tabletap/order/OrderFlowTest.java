package heeyoung.tabletap.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import heeyoung.tabletap.support.IntegrationTest;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class OrderFlowTest extends IntegrationTest {

    @Test
    void customerSeesOnlyWhatCanBeOrderedWithoutLoggingIn() {
        Restaurant r = openRestaurant("Pho Corner");
        String drinks = createCategory(r, "Drinks");
        JsonNode coffee = createItem(r, drinks, "Coffee", 25_000, List.of());
        put("/api/admin/items/" + coffee.get("id").asText() + "/availability", Map.of("available", false), r.owner.cookie());
        createCategory(r, "Empty");

        var response = get("/api/public/" + r.qrToken + "/menu", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode menu = response.getBody();
        assertThat(menu.get("restaurantName").asText()).isEqualTo("Pho Corner");
        assertThat(menu.get("tableLabel").asText()).isEqualTo("T1");
        assertThat(menu.get("currency").asText()).isEqualTo("VND");
        assertThat(menu.get("categories")).extracting(c -> c.get("name").asText()).containsExactly("Noodles");
        JsonNode pho = menu.get("categories").get(0).get("items").get(0);
        assertThat(pho.get("name").asText()).isEqualTo("Pho Bo");
        JsonNode extras = pho.get("optionGroups").get(1);
        assertThat(names(extras.get("options"))).containsExactly("Egg");
        assertThat(extras.get("maxSelect").asInt()).isEqualTo(1);
    }

    @Test
    void itemWhoseRequiredChoiceIsAllSoldOutIsHidden() {
        Restaurant r = openRestaurant("Pho Corner");
        createItem(r, r.categoryId, "Bun Cha", 50_000, List.of(
                group("Size", 1, 1, option("Small", 0, false), option("Big", 10_000, false))));

        JsonNode items = get("/api/public/" + r.qrToken + "/menu", null).getBody().get("categories").get(0).get("items");

        assertThat(names(items)).containsExactly("Pho Bo");
    }

    @Test
    void serverPricesTheOrderAndIgnoresClientPrices() {
        Restaurant r = openRestaurant("Pho Corner");
        Map<String, Object> line = new HashMap<>(line(r.pho, 2, r.option("Large"), r.option("Egg")));
        line.put("unitPrice", 1);
        line.put("note", "  no onions ");

        var placed = post("/api/public/" + r.qrToken + "/orders",
                Map.of("items", List.of(line, line(r.pho, 1, r.option("Regular"))), "note", "Window seat", "total", 1),
                null);

        assertThat(placed.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode order = placed.getBody();
        assertThat(order.get("status").asText()).isEqualTo("PLACED");
        assertThat(order.get("tableLabel").asText()).isEqualTo("T1");
        assertThat(order.get("note").asText()).isEqualTo("Window seat");
        assertThat(order.get("total").asLong()).isEqualTo(2 * 80_000 + 60_000);
        JsonNode first = order.get("items").get(0);
        assertThat(first.get("name").asText()).isEqualTo("Pho Bo");
        assertThat(first.get("unitPrice").asLong()).isEqualTo(80_000);
        assertThat(first.get("lineTotal").asLong()).isEqualTo(160_000);
        assertThat(first.get("note").asText()).isEqualTo("no onions");
        assertThat(first.get("options")).extracting(o -> o.get("optionName").asText()).containsExactly("Large", "Egg");

        JsonNode fetched = get("/api/public/orders/" + order.get("publicToken").asText(), null).getBody();
        assertThat(fetched.get("total").asLong()).isEqualTo(220_000);
        assertThat(fetched.get("restaurantName").asText()).isEqualTo("Pho Corner");
        assertThat(fetched.get("items")).hasSize(2);
    }

    @Test
    void orderBreakingAMenuRuleIsUnprocessable() {
        Restaurant r = openRestaurant("Pho Corner");

        var placed = post("/api/public/" + r.qrToken + "/orders",
                Map.of("items", List.of(line(r.pho, 1, r.option("Egg")))), null);

        assertThat(placed.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(placed.getBody().get("detail").asText()).isEqualTo("Choose at least 1 Size for Pho Bo");
    }

    @Test
    void emptyOrderIsABadRequest() {
        Restaurant r = openRestaurant("Pho Corner");

        assertThat(post("/api/public/" + r.qrToken + "/orders", Map.of("items", List.of()), null).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void soldOutItemCannotBeOrdered() {
        Restaurant r = openRestaurant("Pho Corner");
        put("/api/admin/items/" + r.pho.get("id").asText() + "/availability", Map.of("available", false), r.owner.cookie());

        var placed = post("/api/public/" + r.qrToken + "/orders",
                Map.of("items", List.of(line(r.pho, 1, r.option("Regular")))), null);

        assertThat(placed.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void anotherRestaurantsItemCannotBeOrdered() {
        Restaurant alpha = openRestaurant("Alpha Kitchen");
        Restaurant beta = openRestaurant("Beta Bistro");

        var placed = post("/api/public/" + alpha.qrToken + "/orders",
                Map.of("items", List.of(line(beta.pho, 1, beta.option("Regular")))), null);

        assertThat(placed.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void pastOrdersKeepTheNamesAndPricesTheyWereSoldAt() {
        Restaurant r = openRestaurant("Pho Corner");
        String token = placeOrder(r).get("publicToken").asText();

        delete("/api/admin/items/" + r.pho.get("id").asText(), r.owner.cookie());
        delete("/api/admin/tables/" + r.tableId, r.owner.cookie());

        JsonNode order = get("/api/public/orders/" + token, null).getBody();
        assertThat(order.get("tableLabel").asText()).isEqualTo("T1");
        assertThat(order.get("items").get(0).get("name").asText()).isEqualTo("Pho Bo");
        assertThat(order.get("items").get(0).get("options").get(0).get("optionName").asText()).isEqualTo("Regular");
        assertThat(order.get("total").asLong()).isEqualTo(60_000);
    }

    @Test
    void unknownCodesAndTokensAreNotFound() {
        assertThat(get("/api/public/" + UUID.randomUUID() + "/menu", null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get("/api/public/not-a-token/menu", null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get("/api/public/orders/" + UUID.randomUUID(), null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get("/api/public/orders/not-a-token", null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void turnedOffTableCannotOrder() {
        Restaurant r = openRestaurant("Pho Corner");
        put("/api/admin/tables/" + r.tableId, Map.of("label", "T1", "active", false), r.owner.cookie());

        var placed = post("/api/public/" + r.qrToken + "/orders",
                Map.of("items", List.of(line(r.pho, 1, r.option("Regular")))), null);

        assertThat(placed.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void customerPagesOpenWithoutLogin() {
        Restaurant r = openRestaurant("Pho Corner");
        String token = placeOrder(r).get("publicToken").asText();

        var menuPage = getPage("/t/" + r.qrToken, null);
        assertThat(menuPage.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(menuPage.getBody()).contains("data-qr-token=\"" + r.qrToken + "\"");

        var orderPage = getPage("/t/" + r.qrToken + "/orders/" + token, null);
        assertThat(orderPage.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(orderPage.getBody()).contains("data-public-token=\"" + token + "\"");

        assertThat(getPage("/t/" + r.qrToken + "/orders/" + UUID.randomUUID(), null).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // A restaurant with one table "T1" and a "Noodles" category holding Pho Bo:
    // Size (Regular +0, Large +15,000, required) and Extras (Egg +5,000, sold-out Extra beef, up to 2).
    record Restaurant(Account owner, String qrToken, String tableId, String categoryId, JsonNode pho) {

        String option(String name) {
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

    Restaurant openRestaurant(String name) {
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

    JsonNode placeOrder(Restaurant r) {
        var placed = post("/api/public/" + r.qrToken + "/orders",
                Map.of("items", List.of(line(r.pho, 1, r.option("Regular")))), null);
        assertThat(placed.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return placed.getBody();
    }

    String createCategory(Restaurant r, String name) {
        var response = post("/api/admin/categories", Map.of("name", name, "sortOrder", 0), r.owner.cookie());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().get("id").asText();
    }

    JsonNode createItem(Restaurant r, String categoryId, String name, long price, List<Map<String, Object>> groups) {
        var response = post("/api/admin/items", Map.of(
                "categoryId", categoryId, "name", name, "basePrice", price, "sortOrder", 0, "optionGroups", groups),
                r.owner.cookie());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    static Map<String, Object> line(JsonNode item, int quantity, String... optionIds) {
        return Map.of("menuItemId", item.get("id").asText(), "quantity", quantity, "optionIds", List.of(optionIds));
    }

    @SafeVarargs
    static Map<String, Object> group(String name, int min, int max, Map<String, Object>... options) {
        return Map.of("name", name, "minSelect", min, "maxSelect", max, "options", List.of(options));
    }

    static Map<String, Object> option(String name, long priceDelta, boolean available) {
        return Map.of("name", name, "priceDelta", priceDelta, "available", available);
    }

    static List<String> names(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false).map(n -> n.get("name").asText()).toList();
    }
}
