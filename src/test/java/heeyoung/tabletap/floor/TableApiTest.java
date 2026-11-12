package heeyoung.tabletap.floor;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import heeyoung.tabletap.support.IntegrationTest;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

class TableApiTest extends IntegrationTest {

    @Test
    void newTableGetsACustomerUrl() {
        Account owner = registerRestaurant("Pho Corner");

        var created = post("/api/admin/tables", Map.of("label", "Table 1"), owner.cookie());

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().get("label").asText()).isEqualTo("Table 1");
        assertThat(created.getBody().get("active").asBoolean()).isTrue();
        assertThat(created.getBody().get("url").asText()).matches(".+/t/[0-9a-f-]{36}");
        assertThat(get("/api/admin/tables", owner.cookie()).getBody()).hasSize(1);
    }

    @Test
    void qrImageEncodesTheCustomerUrl() throws Exception {
        Account owner = registerRestaurant("Pho Corner");
        JsonNode table = createTable(owner, "Table 1");

        var image = getBytes("/api/admin/tables/" + table.get("id").asText() + "/qr.png", owner.cookie());

        assertThat(image.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(image.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(decodeQr(image.getBody())).isEqualTo(table.get("url").asText());
    }

    @Test
    void scanningTheCodeOpensTheRestaurantsTablePageWithoutLogin() {
        Account owner = registerRestaurant("Pho Corner");
        JsonNode table = createTable(owner, "Window 7");

        var page = getPage(pathOf(table), null);

        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(page.getBody()).contains("Pho Corner").contains("Window 7");
    }

    @Test
    void restaurantAndTableNamesAreEscapedOnTheCustomerPage() {
        Account owner = registerRestaurant("<script>alert(1)</script>");
        JsonNode table = createTable(owner, "<b>T1</b>");

        var page = getPage(pathOf(table), null);

        assertThat(page.getBody()).doesNotContain("<script>alert(1)</script>").doesNotContain("<b>T1</b>");
        assertThat(page.getBody()).contains("&lt;b&gt;T1&lt;/b&gt;").contains("&lt;script&gt;");
    }

    @Test
    void regeneratingTheCodeInvalidatesTheOldOne() throws Exception {
        Account owner = registerRestaurant("Pho Corner");
        JsonNode table = createTable(owner, "Table 1");
        String id = table.get("id").asText();

        var regenerated = post("/api/admin/tables/" + id + "/regenerate-qr", null, owner.cookie());

        assertThat(regenerated.getStatusCode()).isEqualTo(HttpStatus.OK);
        String newUrl = regenerated.getBody().get("url").asText();
        assertThat(newUrl).isNotEqualTo(table.get("url").asText());
        assertThat(getPage(pathOf(table), null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(getPage(pathOf(regenerated.getBody()), null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(decodeQr(getBytes("/api/admin/tables/" + id + "/qr.png", owner.cookie()).getBody()))
                .isEqualTo(newUrl);
    }

    @Test
    void turnedOffTableStopsAcceptingScans() {
        Account owner = registerRestaurant("Pho Corner");
        JsonNode table = createTable(owner, "Table 1");
        String id = table.get("id").asText();

        put("/api/admin/tables/" + id, Map.of("label", "Table 1", "active", false), owner.cookie());
        assertThat(getPage(pathOf(table), null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        put("/api/admin/tables/" + id, Map.of("label", "Table 1", "active", true), owner.cookie());
        assertThat(getPage(pathOf(table), null).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void unknownOrMalformedCodesAreNotFound() {
        assertThat(getPage("/t/" + UUID.randomUUID(), null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(getPage("/t/not-a-token", null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deletedTableStopsAcceptingScans() {
        Account owner = registerRestaurant("Pho Corner");
        JsonNode table = createTable(owner, "Table 1");

        assertThat(delete("/api/admin/tables/" + table.get("id").asText(), owner.cookie()).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(getPage(pathOf(table), null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get("/api/admin/tables", owner.cookie()).getBody()).isEmpty();
    }

    @Test
    void tableCanBeRenamed() {
        Account owner = registerRestaurant("Pho Corner");
        JsonNode table = createTable(owner, "Table 1");

        var renamed = put("/api/admin/tables/" + table.get("id").asText(), Map.of("label", "Patio 1"), owner.cookie());

        assertThat(renamed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(renamed.getBody().get("url").asText()).isEqualTo(table.get("url").asText());
        assertThat(getPage(pathOf(table), null).getBody()).contains("Patio 1");
    }

    @Test
    void labelsAreUniqueWithinARestaurant() {
        Account owner = registerRestaurant("Pho Corner");
        createTable(owner, "Table 1");
        JsonNode second = createTable(owner, "Table 2");

        assertThat(post("/api/admin/tables", Map.of("label", "Table 1"), owner.cookie()).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(put("/api/admin/tables/" + second.get("id").asText(), Map.of("label", "Table 1"), owner.cookie())
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(put("/api/admin/tables/" + second.get("id").asText(), Map.of("label", "Table 2"), owner.cookie())
                .getStatusCode()).as("keeping its own label").isEqualTo(HttpStatus.OK);
        assertThat(post("/api/admin/tables", Map.of("label", "  Table 1  "), owner.cookie()).getStatusCode())
                .as("surrounding spaces").isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void accentedLabelsTypedInDifferentUnicodeFormsAreTheSameLabel() {
        // The two labels below look identical but differ in bytes: the first uses
        // one precomposed character, the second a plain "a" plus a combining accent.
        Account owner = registerRestaurant("Pho Corner");
        createTable(owner, "Bàn 1");

        var decomposed = post("/api/admin/tables", Map.of("label", "Bàn 1"), owner.cookie());

        assertThat(decomposed.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void tableCanBeCreatedTurnedOff() {
        Account owner = registerRestaurant("Pho Corner");

        var created = post("/api/admin/tables", Map.of("label", "Table 1", "active", false), owner.cookie());

        assertThat(created.getBody().get("active").asBoolean()).isFalse();
        assertThat(getPage(pathOf(created.getBody()), null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void twoRestaurantsCanUseTheSameLabel() {
        Account alpha = registerRestaurant("Alpha Kitchen");
        Account beta = registerRestaurant("Beta Bistro");

        JsonNode alphaTable = createTable(alpha, "Table 1");
        JsonNode betaTable = createTable(beta, "Table 1");

        assertThat(getPage(pathOf(alphaTable), null).getBody()).contains("Alpha Kitchen");
        assertThat(getPage(pathOf(betaTable), null).getBody()).contains("Beta Bistro");
    }

    @Test
    void blankLabelIsRejected() {
        Account owner = registerRestaurant("Pho Corner");

        assertThat(post("/api/admin/tables", Map.of("label", " "), owner.cookie()).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void tableApiRequiresLogin() {
        assertThat(get("/api/admin/tables", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(getBytes("/api/admin/tables/" + UUID.randomUUID() + "/qr.png", null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void anotherTenantCannotSeeOrChangeTables() {
        Account alpha = registerRestaurant("Alpha Kitchen");
        Account beta = registerRestaurant("Beta Bistro");
        JsonNode table = createTable(alpha, "Table 1");
        String path = "/api/admin/tables/" + table.get("id").asText();

        assertThat(get("/api/admin/tables", beta.cookie()).getBody()).isEmpty();
        assertThat(getBytes(path + "/qr.png", beta.cookie()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(put(path, Map.of("label", "Hacked", "active", false), beta.cookie()).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(post(path + "/regenerate-qr", null, beta.cookie()).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(delete(path, beta.cookie()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        JsonNode unchanged = get("/api/admin/tables", alpha.cookie()).getBody().get(0);
        assertThat(unchanged.get("label").asText()).isEqualTo("Table 1");
        assertThat(unchanged.get("url").asText()).isEqualTo(table.get("url").asText());
        assertThat(getPage(pathOf(table), null).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void restaurantEndpointReturnsTheCallersRestaurant() {
        Account alpha = registerRestaurant("Alpha Kitchen");
        Account beta = registerRestaurant("Beta Bistro");

        assertThat(get("/api/admin/restaurant", alpha.cookie()).getBody().get("name").asText())
                .isEqualTo("Alpha Kitchen");
        assertThat(get("/api/admin/restaurant", beta.cookie()).getBody().get("name").asText())
                .isEqualTo("Beta Bistro");
    }

    @Test
    void tablePagesNeedLogin() {
        Account owner = registerRestaurant("Pho Corner");

        assertThat(getPage("/admin/tables", null).getBody()).contains("Log in to manage your restaurant");
        assertThat(getPage("/admin/tables", owner.cookie()).getBody()).contains("Add table");
        assertThat(getPage("/admin/tables/print", null).getBody()).contains("Log in to manage your restaurant");
        assertThat(getPage("/admin/tables/print", owner.cookie()).getBody()).contains("print-status");
    }

    private JsonNode createTable(Account owner, String label) {
        var response = post("/api/admin/tables", Map.of("label", label), owner.cookie());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private static String pathOf(JsonNode table) {
        return URI.create(table.get("url").asText()).getPath();
    }

    private static String decodeQr(byte[] png) throws Exception {
        var image = ImageIO.read(new ByteArrayInputStream(png));
        var bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));
        return new QRCodeReader().decode(bitmap).getText();
    }
}
