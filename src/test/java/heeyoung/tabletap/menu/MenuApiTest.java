package heeyoung.tabletap.menu;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import heeyoung.tabletap.support.IntegrationTest;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class MenuApiTest extends IntegrationTest {

    @Test
    void ownerBuildsAnItemWithOptionGroups() {
        Account owner = registerRestaurant("Pho Corner");
        String categoryId = createCategory(owner, "Noodles");

        var created = post("/api/admin/items", pho(categoryId), owner.cookie());

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode item = get("/api/admin/items/" + created.getBody().get("id").asText(), owner.cookie()).getBody();
        assertThat(item.get("name").asText()).isEqualTo("Pho Bo");
        assertThat(item.get("basePrice").asLong()).isEqualTo(60000);
        assertThat(item.get("available").asBoolean()).isTrue();
        assertThat(item.get("optionGroups")).hasSize(2);
        JsonNode size = item.get("optionGroups").get(0);
        assertThat(size.get("name").asText()).isEqualTo("Size");
        assertThat(size.get("minSelect").asInt()).isEqualTo(1);
        assertThat(size.get("maxSelect").asInt()).isEqualTo(1);
        assertThat(size.get("options")).extracting(o -> o.get("name").asText()).containsExactly("Regular", "Large");
        assertThat(size.get("options").get(1).get("priceDelta").asLong()).isEqualTo(15000);
        assertThat(item.get("optionGroups").get(1).get("name").asText()).isEqualTo("Extras");
    }

    @Test
    void categoriesAreListedInSortOrder() {
        Account owner = registerRestaurant("Pho Corner");
        post("/api/admin/categories", Map.of("name", "Drinks", "sortOrder", 2), owner.cookie());
        post("/api/admin/categories", Map.of("name", "Noodles", "sortOrder", 1), owner.cookie());

        JsonNode list = get("/api/admin/categories", owner.cookie()).getBody();

        assertThat(list).extracting(c -> c.get("name").asText()).containsExactly("Noodles", "Drinks");
    }

    @Test
    void categoryCanBeRenamed() {
        Account owner = registerRestaurant("Pho Corner");
        String categoryId = createCategory(owner, "Noodles");

        var updated = put("/api/admin/categories/" + categoryId, Map.of("name", "Soups", "sortOrder", 3), owner.cookie());

        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/api/admin/categories", owner.cookie()).getBody().get(0).get("name").asText())
                .isEqualTo("Soups");
    }

    @Test
    void updatingAnItemReplacesItsOptionGroups() {
        Account owner = registerRestaurant("Pho Corner");
        String categoryId = createCategory(owner, "Noodles");
        String itemId = post("/api/admin/items", pho(categoryId), owner.cookie()).getBody().get("id").asText();

        Map<String, Object> changed = new HashMap<>(pho(categoryId));
        changed.put("name", "Pho Ga");
        changed.put("basePrice", 55000);
        changed.put("optionGroups", List.of(group("Spice", 0, 1, option("Mild", 0), option("Hot", 0))));
        var updated = put("/api/admin/items/" + itemId, changed, owner.cookie());

        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode item = get("/api/admin/items/" + itemId, owner.cookie()).getBody();
        assertThat(item.get("name").asText()).isEqualTo("Pho Ga");
        assertThat(item.get("basePrice").asLong()).isEqualTo(55000);
        assertThat(item.get("optionGroups")).extracting(g -> g.get("name").asText()).containsExactly("Spice");
    }

    @Test
    void itemCanBeMarkedSoldOut() {
        Account owner = registerRestaurant("Pho Corner");
        String itemId = createItem(owner, createCategory(owner, "Noodles"));

        var response = put("/api/admin/items/" + itemId + "/availability", Map.of("available", false), owner.cookie());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode item = get("/api/admin/items/" + itemId, owner.cookie()).getBody();
        assertThat(item.get("available").asBoolean()).isFalse();
        assertThat(item.get("optionGroups")).hasSize(2);
    }

    @Test
    void deletingAnItemRemovesIt() {
        Account owner = registerRestaurant("Pho Corner");
        String itemId = createItem(owner, createCategory(owner, "Noodles"));

        assertThat(delete("/api/admin/items/" + itemId, owner.cookie()).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(get("/api/admin/items/" + itemId, owner.cookie()).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get("/api/admin/items", owner.cookie()).getBody()).isEmpty();
    }

    @Test
    void categoryWithItemsCannotBeDeleted() {
        Account owner = registerRestaurant("Pho Corner");
        String categoryId = createCategory(owner, "Noodles");
        String itemId = createItem(owner, categoryId);

        assertThat(delete("/api/admin/categories/" + categoryId, owner.cookie()).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        delete("/api/admin/items/" + itemId, owner.cookie());
        assertThat(delete("/api/admin/categories/" + categoryId, owner.cookie()).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(get("/api/admin/categories", owner.cookie()).getBody()).isEmpty();
    }

    @Test
    void invalidItemsAreRejected() {
        Account owner = registerRestaurant("Pho Corner");
        String categoryId = createCategory(owner, "Noodles");

        assertThat(statusOfItemWith(owner, categoryId, "name", " ")).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(statusOfItemWith(owner, categoryId, "basePrice", -1)).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(statusOfItemWith(owner, categoryId, "optionGroups",
                List.of(group("Size", 2, 1, option("Regular", 0), option("Large", 15000)))))
                .as("maxSelect below minSelect").isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(statusOfItemWith(owner, categoryId, "optionGroups",
                List.of(group("Size", 3, 3, option("Regular", 0), option("Large", 15000)))))
                .as("minSelect above option count").isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(statusOfItemWith(owner, categoryId, "optionGroups", List.of(group("Size", 0, 1))))
                .as("group without options").isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(statusOfItemWith(owner, categoryId, "optionGroups",
                List.of(group("Size", 0, 1, option("Regular", -500)))))
                .as("negative price delta").isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(statusOfItemWith(owner, categoryId, "basePrice", 5_000_000_000_000L))
                .as("price above the limit").isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(statusOfItemWith(owner, categoryId, "optionGroups", Collections.singletonList(null)))
                .as("null option group").isEqualTo(HttpStatus.BAD_REQUEST);
        Map<String, Object> groupWithNullOption = new HashMap<>(group("Size", 0, 1));
        groupWithNullOption.put("options", Collections.singletonList(null));
        assertThat(statusOfItemWith(owner, categoryId, "optionGroups", List.of(groupWithNullOption)))
                .as("null option").isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(get("/api/admin/items", owner.cookie()).getBody()).isEmpty();
    }

    @Test
    void itemCannotBeMovedIntoAnotherTenantsCategory() {
        Account alpha = registerRestaurant("Alpha Kitchen");
        Account beta = registerRestaurant("Beta Bistro");
        String alphaCategoryId = createCategory(alpha, "Noodles");
        String betaCategoryId = createCategory(beta, "Starters");
        String betaItemId = createItem(beta, betaCategoryId);

        var response = put("/api/admin/items/" + betaItemId, pho(alphaCategoryId), beta.cookie());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        JsonNode item = get("/api/admin/items/" + betaItemId, beta.cookie()).getBody();
        assertThat(item.get("categoryId").asText()).isEqualTo(betaCategoryId);
        assertThat(item.get("optionGroups")).hasSize(2);
        assertThat(get("/api/admin/items", alpha.cookie()).getBody()).isEmpty();
    }

    @Test
    void blankCategoryNameIsRejected() {
        Account owner = registerRestaurant("Pho Corner");

        var response = post("/api/admin/categories", Map.of("name", "", "sortOrder", 0), owner.cookie());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void itemNeedsAnExistingCategory() {
        Account owner = registerRestaurant("Pho Corner");

        var response = post("/api/admin/items", pho(java.util.UUID.randomUUID().toString()), owner.cookie());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void menuApiRequiresLogin() {
        assertThat(get("/api/admin/categories", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get("/api/admin/items", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void anotherTenantCannotSeeTheMenu() {
        Account alpha = registerRestaurant("Alpha Kitchen");
        Account beta = registerRestaurant("Beta Bistro");
        String categoryId = createCategory(alpha, "Noodles");
        String itemId = createItem(alpha, categoryId);

        assertThat(get("/api/admin/categories", beta.cookie()).getBody()).isEmpty();
        assertThat(get("/api/admin/items", beta.cookie()).getBody()).isEmpty();
        assertThat(get("/api/admin/items/" + itemId, beta.cookie()).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void anotherTenantCannotChangeTheMenu() {
        Account alpha = registerRestaurant("Alpha Kitchen");
        Account beta = registerRestaurant("Beta Bistro");
        String categoryId = createCategory(alpha, "Noodles");
        String itemId = createItem(alpha, categoryId);
        String betaCategoryId = createCategory(beta, "Starters");

        assertThat(put("/api/admin/categories/" + categoryId, Map.of("name", "Hacked", "sortOrder", 0), beta.cookie())
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(delete("/api/admin/categories/" + categoryId, beta.cookie()).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(put("/api/admin/items/" + itemId, pho(betaCategoryId), beta.cookie()).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(put("/api/admin/items/" + itemId + "/availability", Map.of("available", false), beta.cookie())
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(delete("/api/admin/items/" + itemId, beta.cookie()).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(post("/api/admin/items", pho(categoryId), beta.cookie()).getStatusCode())
                .as("item in another tenant's category").isEqualTo(HttpStatus.NOT_FOUND);

        JsonNode item = get("/api/admin/items/" + itemId, alpha.cookie()).getBody();
        assertThat(item.get("name").asText()).isEqualTo("Pho Bo");
        assertThat(item.get("available").asBoolean()).isTrue();
        assertThat(get("/api/admin/categories", alpha.cookie()).getBody().get(0).get("name").asText())
                .isEqualTo("Noodles");
        assertThat(get("/api/admin/items", alpha.cookie()).getBody()).hasSize(1);
    }

    private String createCategory(Account owner, String name) {
        var response = post("/api/admin/categories", Map.of("name", name, "sortOrder", 0), owner.cookie());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().get("id").asText();
    }

    private String createItem(Account owner, String categoryId) {
        var response = post("/api/admin/items", pho(categoryId), owner.cookie());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().get("id").asText();
    }

    private HttpStatus statusOfItemWith(Account owner, String categoryId, String field, Object value) {
        Map<String, Object> body = new HashMap<>(pho(categoryId));
        body.put(field, value);
        return HttpStatus.valueOf(post("/api/admin/items", body, owner.cookie()).getStatusCode().value());
    }

    private static Map<String, Object> pho(String categoryId) {
        return Map.of(
                "categoryId", categoryId,
                "name", "Pho Bo",
                "description", "Beef noodle soup",
                "basePrice", 60000,
                "sortOrder", 0,
                "optionGroups", List.of(
                        group("Size", 1, 1, option("Regular", 0), option("Large", 15000)),
                        group("Extras", 0, 2, option("Egg", 5000), option("Extra beef", 20000))));
    }

    @SafeVarargs
    private static Map<String, Object> group(String name, int min, int max, Map<String, Object>... options) {
        return Map.of("name", name, "minSelect", min, "maxSelect", max, "options", List.of(options));
    }

    private static Map<String, Object> option(String name, long priceDelta) {
        return Map.of("name", name, "priceDelta", priceDelta);
    }
}
