package heeyoung.tabletap.kitchen;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import heeyoung.tabletap.support.OrderingTest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class KitchenApiTest extends OrderingTest {

    @Test
    void ordersAreListedOldestFirstAndFilteredByStatus() {
        Restaurant r = openRestaurant("Pho Corner");
        String first = placeOrder(r).get("id").asText();
        String second = placeOrder(r).get("id").asText();
        transition(r, second, "ACCEPTED");

        JsonNode all = get("/api/admin/orders", r.owner().cookie()).getBody();
        JsonNode placed = get("/api/admin/orders?status=PLACED", r.owner().cookie()).getBody();
        JsonNode open = get("/api/admin/orders?status=PLACED&status=ACCEPTED", r.owner().cookie()).getBody();

        assertThat(ids(all)).containsExactly(first, second);
        assertThat(ids(placed)).containsExactly(first);
        assertThat(ids(open)).containsExactly(first, second);
        assertThat(all.get(0).get("nextStatuses")).extracting(JsonNode::asText)
                .containsExactlyInAnyOrder("ACCEPTED", "CANCELLED");
    }

    @Test
    void orderMovesThroughTheWholeLifecycleAndTheCustomerSeesIt() {
        Restaurant r = openRestaurant("Pho Corner");
        JsonNode order = placeOrder(r);
        String id = order.get("id").asText();

        for (String status : List.of("ACCEPTED", "PREPARING", "SERVED", "PAID")) {
            var moved = transition(r, id, status);
            assertThat(moved.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(moved.getBody().get("status").asText()).isEqualTo(status);
        }

        JsonNode seen = get("/api/public/orders/" + order.get("publicToken").asText(), null).getBody();
        assertThat(seen.get("status").asText()).isEqualTo("PAID");
        assertThat(seen.get("nextStatuses")).isEmpty();
    }

    @Test
    void illegalTransitionIsAConflict() {
        Restaurant r = openRestaurant("Pho Corner");
        String id = placeOrder(r).get("id").asText();

        var skipped = transition(r, id, "SERVED");

        assertThat(skipped.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(skipped.getBody().get("detail").asText()).isEqualTo("An order that is PLACED cannot become SERVED");
        transition(r, id, "CANCELLED");
        assertThat(transition(r, id, "ACCEPTED").getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void unknownStatusIsABadRequest() {
        Restaurant r = openRestaurant("Pho Corner");
        String id = placeOrder(r).get("id").asText();

        assertThat(transition(r, id, "EATEN").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void anotherTenantCannotSeeOrMoveOrders() {
        Restaurant alpha = openRestaurant("Alpha Kitchen");
        Restaurant beta = openRestaurant("Beta Bistro");
        String alphaOrder = placeOrder(alpha).get("id").asText();

        assertThat(get("/api/admin/orders", beta.owner().cookie()).getBody()).isEmpty();
        var moved = post("/api/admin/orders/" + alphaOrder + "/transition", Map.of("to", "ACCEPTED"), beta.owner().cookie());
        assertThat(moved.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get("/api/admin/orders?status=PLACED", alpha.owner().cookie()).getBody()).hasSize(1);
    }

    @Test
    void kitchenApiAndPageNeedLogin() {
        assertThat(get("/api/admin/orders", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(post("/api/admin/orders/" + UUID.randomUUID() + "/transition", Map.of("to", "PAID"), null)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // The test client follows the redirect to the login page.
        assertThat(getPage("/admin/kitchen", null).getBody()).contains("Log in to manage your restaurant");
    }

    @Test
    void kitchenPageOpensForStaff() {
        Restaurant r = openRestaurant("Pho Corner");

        var page = getPage("/admin/kitchen", r.owner().cookie());

        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(page.getBody()).contains("/js/kitchen.js");
    }

    @Test
    void streamDeliversOnlyTheRestaurantsOwnOrders() throws Exception {
        Restaurant alpha = openRestaurant("Alpha Kitchen");
        Restaurant beta = openRestaurant("Beta Bistro");

        try (var stream = openStream(alpha)) {
            String betaToken = placeOrder(beta).get("publicToken").asText();
            String alphaToken = placeOrder(alpha).get("publicToken").asText();
            transition(alpha, idOf(alpha, alphaToken), "ACCEPTED");

            List<String> data = stream.dataLines(2);

            assertThat(data.get(0)).contains(alphaToken).contains("\"status\":\"PLACED\"");
            assertThat(data.get(1)).contains(alphaToken).contains("\"status\":\"ACCEPTED\"");
            assertThat(stream.everything()).noneMatch(line -> line.contains(betaToken));
        }
    }

    @Test
    void streamNeedsLogin() throws Exception {
        var response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(rest.getRootUri() + "/api/admin/orders/stream")).build(),
                HttpResponse.BodyHandlers.discarding());

        assertThat(response.statusCode()).isEqualTo(401);
    }

    private org.springframework.http.ResponseEntity<JsonNode> transition(Restaurant r, String id, String status) {
        return post("/api/admin/orders/" + id + "/transition", Map.of("to", status), r.owner().cookie());
    }

    private String idOf(Restaurant r, String publicToken) {
        for (JsonNode order : get("/api/admin/orders", r.owner().cookie()).getBody()) {
            if (order.get("publicToken").asText().equals(publicToken)) {
                return order.get("id").asText();
            }
        }
        throw new AssertionError("No order " + publicToken);
    }

    private static List<String> ids(JsonNode orders) {
        return StreamSupport.stream(orders.spliterator(), false).map(order -> order.get("id").asText()).toList();
    }

    private EventStream openStream(Restaurant r) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        var response = client.send(
                HttpRequest.newBuilder(URI.create(rest.getRootUri() + "/api/admin/orders/stream"))
                        .header("Cookie", r.owner().cookie())
                        .build(),
                HttpResponse.BodyHandlers.ofLines());
        assertThat(response.statusCode()).isEqualTo(200);
        return new EventStream(client, response.body());
    }

    // Reads the event stream on its own thread so the test can wait with a timeout.
    private static final class EventStream implements AutoCloseable {

        private final HttpClient client;
        private final BlockingQueue<String> lines = new LinkedBlockingQueue<>();
        private final List<String> seen = new ArrayList<>();
        private final Thread reader;

        EventStream(HttpClient client, Stream<String> body) {
            this.client = client;
            this.reader = Thread.ofVirtual().start(() -> {
                try (body) {
                    body.forEach(lines::add);
                } catch (RuntimeException closed) {
                    // The test closed the connection.
                }
            });
        }

        List<String> dataLines(int count) throws InterruptedException {
            List<String> data = new ArrayList<>();
            long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            while (data.size() < count) {
                String line = lines.poll(deadline - System.nanoTime(), TimeUnit.NANOSECONDS);
                if (line == null) {
                    throw new AssertionError("Expected " + count + " events, got " + data + " after " + seen);
                }
                seen.add(line);
                if (line.startsWith("data:")) {
                    data.add(line);
                }
            }
            return data;
        }

        List<String> everything() {
            lines.drainTo(seen);
            return seen;
        }

        @Override
        public void close() {
            reader.interrupt();
            client.shutdownNow();
        }
    }
}
