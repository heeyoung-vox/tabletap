package heeyoung.tabletap;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import heeyoung.tabletap.support.OrderingTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntFunction;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

/** Requests that really arrive at the same moment, over HTTP. */
class ConcurrencyTest extends OrderingTest {

    private static final int CLIENTS = 10;
    private static final int ROUNDS = 15;

    @Test
    void onlyOneOfManyIdenticalTransitionsWins() {
        Restaurant r = openRestaurant("Pho Corner");
        String id = placeOrder(r).get("id").asText();

        List<Integer> statuses = statuses(atOnce(CLIENTS, i -> transition(r, id, "ACCEPTED")));

        assertThat(statuses).containsOnly(200, 409);
        assertThat(statuses).filteredOn(status -> status == 200).hasSize(1);
    }

    @Test
    void acceptAndCancelRacingLeaveOneConsistentOutcome() {
        Restaurant r = openRestaurant("Pho Corner");
        String id = placeOrder(r).get("id").asText();

        var responses = atOnce(CLIENTS, i -> transition(r, id, i % 2 == 0 ? "ACCEPTED" : "CANCELLED"));

        assertThat(statuses(responses)).containsOnly(200, 409);
        long accepted = wins(responses, "ACCEPTED");
        long cancelled = wins(responses, "CANCELLED");
        assertThat(accepted).isLessThanOrEqualTo(1);
        assertThat(cancelled).isLessThanOrEqualTo(1);
        assertThat(accepted + cancelled).isPositive();
        String finalStatus = get("/api/admin/orders", r.owner().cookie()).getBody().findValuesAsText("status").get(0);
        assertThat(finalStatus).isEqualTo(cancelled == 1 ? "CANCELLED" : "ACCEPTED");
    }

    @Test
    void oneEmailRegistersOnce() {
        String email = uniqueEmail();

        List<Integer> statuses = statuses(atOnce(CLIENTS, i -> register("Race Cafe", email)));

        assertThat(statuses).containsOnly(201, 409);
        assertThat(statuses).filteredOn(status -> status == 201).hasSize(1);
    }

    @Test
    void restaurantsRegisteringTheSameNameAtOnceAllGetTheirOwnSlug() {
        String name = "Race Cafe " + UUID.randomUUID().toString().substring(0, 8);

        var responses = atOnce(CLIENTS, i -> register(name, uniqueEmail()));

        assertThat(statuses(responses)).containsOnly(201);
        List<String> slugs = responses.stream()
                .map(response -> get("/api/admin/restaurant", sessionCookie(response)).getBody().get("slug").asText())
                .toList();
        assertThat(slugs).doesNotHaveDuplicates();
    }

    @Test
    void oneLabelMakesOneTable() {
        Account owner = registerRestaurant("Pho Corner");

        List<Integer> statuses = statuses(atOnce(CLIENTS,
                i -> post("/api/admin/tables", Map.of("label", "Window"), owner.cookie())));

        assertThat(statuses).containsOnly(201, 409);
        assertThat(statuses).filteredOn(status -> status == 201).hasSize(1);
        assertThat(get("/api/admin/tables", owner.cookie()).getBody()).hasSize(1);
    }

    @Test
    void deletingACategoryWhileAnItemIsAddedToItNeverFails() {
        Restaurant r = openRestaurant("Pho Corner");

        for (int round = 0; round < ROUNDS; round++) {
            String categoryId = createCategory(r, "Specials " + round);
            var item = Map.of("categoryId", categoryId, "name", "Special", "basePrice", 10_000, "sortOrder", 0);

            List<Integer> statuses = statuses(atOnce(2, i -> i == 0
                    ? delete("/api/admin/categories/" + categoryId, r.owner().cookie())
                    : post("/api/admin/items", item, r.owner().cookie())));

            assertThat(statuses).as("delete, create in round %d", round)
                    .isIn(List.of(204, 404), List.of(409, 201), List.of(204, 409));
        }
    }

    @Test
    void orderPlacedWhileItsItemIsEditedIsPricedFromOneMenuOrRejected() {
        Restaurant r = openRestaurant("Pho Corner");

        for (int round = 0; round < ROUNDS; round++) {
            JsonNode item = createItem(r, r.categoryId(), "Bun Cha " + round, 60_000, List.of(
                    group("Size", 1, 1, option("Regular", 0, true), option("Large", 15_000, true))));
            String large = item.get("optionGroups").get(0).get("options").get(1).get("id").asText();
            var edited = Map.of("categoryId", r.categoryId(), "name", "Bun Cha " + round, "basePrice", 70_000,
                    "sortOrder", 0, "optionGroups", List.of(
                            group("Size", 1, 1, option("Regular", 0, true), option("Large", 25_000, true))));

            var responses = atOnce(2, i -> i == 0
                    ? put("/api/admin/items/" + item.get("id").asText(), edited, r.owner().cookie())
                    : post("/api/public/" + r.qrToken() + "/orders", Map.of("items", List.of(line(item, 1, large))), null));

            assertThat(responses.get(0).getStatusCode().value()).as("edit in round %d", round).isEqualTo(200);
            var order = responses.get(1);
            assertThat(order.getStatusCode().value()).as("order in round %d", round).isIn(201, 422);
            if (order.getStatusCode().value() == 201) {
                assertThat(order.getBody().get("total").asLong()).isEqualTo(75_000);
            }
        }
    }

    private ResponseEntity<JsonNode> transition(Restaurant r, String id, String status) {
        return post("/api/admin/orders/" + id + "/transition", Map.of("to", status), r.owner().cookie());
    }

    private ResponseEntity<JsonNode> register(String restaurantName, String email) {
        return post("/api/auth/register",
                Map.of("restaurantName", restaurantName, "email", email, "password", PASSWORD), null);
    }

    private static List<Integer> statuses(List<ResponseEntity<JsonNode>> responses) {
        return responses.stream().map(response -> response.getStatusCode().value()).toList();
    }

    private static long wins(List<ResponseEntity<JsonNode>> responses, String status) {
        return responses.stream()
                .filter(response -> response.getStatusCode().value() == 200)
                .filter(response -> response.getBody().get("status").asText().equals(status))
                .count();
    }

    // Every caller waits at the barrier, so the requests leave together.
    private static <T> List<T> atOnce(int callers, IntFunction<T> call) {
        CyclicBarrier barrier = new CyclicBarrier(callers);
        try (ExecutorService pool = Executors.newFixedThreadPool(callers)) {
            List<Future<T>> pending = IntStream.range(0, callers)
                    .mapToObj(i -> pool.submit(() -> {
                        barrier.await();
                        return call.apply(i);
                    }))
                    .toList();
            return pending.stream().map(ConcurrencyTest::result).toList();
        }
    }

    private static <T> T result(Future<T> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
