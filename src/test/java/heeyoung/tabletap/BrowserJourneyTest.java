package heeyoung.tabletap;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.assertions.LocatorAssertions;
import com.microsoft.playwright.options.Cookie;
import heeyoung.tabletap.support.OrderingTest;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Drives the real pages in a browser. Set BROWSER_CHANNEL to "chrome" or "msedge"
 * to use an installed browser. Without it Playwright downloads its own Chromium.
 */
@Tag("browser")
class BrowserJourneyTest extends OrderingTest {

    private static final String MARKUP = "<img src=x onerror=window.__xss=1>";

    private static Playwright playwright;
    private static Browser browser;

    private String base;
    private BrowserContext context;

    @BeforeAll
    static void launchBrowser() {
        String channel = System.getenv("BROWSER_CHANNEL");
        var launch = new BrowserType.LaunchOptions();
        var create = new Playwright.CreateOptions();
        if (channel != null && !channel.isBlank()) {
            launch.setChannel(channel);
            create.setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1"));
        }
        playwright = Playwright.create(create);
        browser = playwright.chromium().launch(launch);
    }

    @AfterAll
    static void closeBrowser() {
        if (playwright != null) {
            playwright.close();
        }
    }

    @BeforeEach
    void openContext() {
        base = rest.getRootUri();
        context = browser.newContext(new Browser.NewContextOptions()
                .setLocale("en-US")
                .setViewportSize(390, 844)
                .setHasTouch(true));
        context.setDefaultTimeout(10_000);
    }

    @AfterEach
    void closeContext() {
        context.close();
    }

    @Test
    void customerOrdersFromAPhoneAndTheCartSurvivesAReload() {
        Restaurant r = openRestaurant("Pho Corner");
        Page page = context.newPage();
        page.navigate(base + "/t/" + r.qrToken());

        dish(page, "Pho Bo").click();
        Locator add = page.locator("#item-form button[type=submit]");
        assertThat(add).containsText("Add 1 to order");
        choice(page, "Large").check();
        assertThat(add).containsText("75,000");
        add.click();
        assertThat(page.locator("#open-cart")).containsText("1 item");

        page.reload();
        assertThat(page.locator("#open-cart")).containsText("1 item");
        assertThat(page.locator("#open-cart")).containsText("75,000");

        page.locator("#open-cart").click();
        page.locator("#order-note").fill("No onions");
        page.locator("#place-order").click();

        page.waitForURL("**/orders/**");
        assertThat(page.locator("#status-text")).containsText("sent to the kitchen");
        assertThat(page.locator("#lines")).containsText("1 × Pho Bo");
        assertThat(page.locator("#lines")).containsText("Large");
        assertThat(page.locator("#note")).containsText("No onions");
        assertThat(page.locator("#total")).containsText("75,000");

        page.getByText("Order more").click();
        assertThat(page.locator("#my-orders a")).hasCount(1);
        assertThat(page.locator("#cart-bar")).isHidden();
    }

    @Test
    void choicesFollowTheGroupsMinimumAndMaximum() {
        Restaurant r = openRestaurant("Pho Corner");
        createItem(r, r.categoryId(), "Bun Thit", 50_000, List.of(group("Toppings", 1, 2,
                option("Herbs", 0, true), option("Chili", 0, true), option("Lime", 0, true))));
        Page page = context.newPage();
        page.navigate(base + "/t/" + r.qrToken());

        dish(page, "Bun Thit").click();
        Locator add = page.locator("#item-form button[type=submit]");
        assertThat(add).isDisabled();
        assertThat(add).hasText("Make your choices above");

        choice(page, "Herbs").check();
        assertThat(add).isEnabled();
        choice(page, "Chili").check();
        assertThat(choice(page, "Lime")).isDisabled();
        choice(page, "Chili").uncheck();
        assertThat(choice(page, "Lime")).isEnabled();
        page.getByText("Cancel").click();

        dish(page, "Pho Bo").click();
        assertThat(page.locator("#item-form")).containsText("Egg");
        assertThat(page.locator("#item-form")).not().containsText("Extra beef");
    }

    @Test
    void itemThatSellsOutWhileInTheCartIsRemovedWithAMessage() {
        Restaurant r = openRestaurant("Pho Corner");
        createItem(r, r.categoryId(), "Tra Da", 5_000, List.of());
        Page page = context.newPage();
        page.navigate(base + "/t/" + r.qrToken());
        dish(page, "Pho Bo").click();
        page.locator("#item-form button[type=submit]").click();
        dish(page, "Tra Da").click();
        page.locator("#item-form button[type=submit]").click();
        assertThat(page.locator("#open-cart")).containsText("2 items");

        put("/api/admin/items/" + r.pho().get("id").asText() + "/availability",
                Map.of("available", false), r.owner().cookie());
        page.locator("#open-cart").click();
        page.locator("#place-order").click();

        assertThat(page.locator("#cart-error")).containsText("no longer available");
        assertThat(page.locator("#notice")).containsText("removed");
        assertThat(page.locator("#cart-lines")).containsText("Tra Da");
        assertThat(page.locator("#cart-lines")).not().containsText("Pho Bo");

        page.locator("#place-order").click();
        page.waitForURL("**/orders/**");
        assertThat(page.locator("#total")).containsText("5,000");
    }

    @Test
    void kitchenBoardShowsANewOrderLiveAndMovesItToPaid() {
        Restaurant r = openRestaurant("Pho Corner");
        Page page = staffPage(r, "/admin/kitchen");
        assertThat(page.locator("#live")).hasText("Live");

        JsonNode order = placeOrder(r);

        assertThat(lane(page, "PLACED").locator(".ticket")).containsText("Table T1");
        assertThat(lane(page, "PLACED").locator(".ticket")).containsText("Pho Bo");
        assertThat(page).hasTitle("(1) Kitchen · TableTap");

        ticketButton(page, "Accept").click();
        assertThat(lane(page, "ACCEPTED").locator(".ticket")).hasCount(1);
        ticketButton(page, "Start cooking").click();
        assertThat(lane(page, "PREPARING").locator(".ticket")).hasCount(1);
        ticketButton(page, "Mark served").click();
        assertThat(lane(page, "SERVED").locator(".ticket")).hasCount(1);
        ticketButton(page, "Mark paid").click();
        assertThat(page.locator(".ticket")).hasCount(0);

        JsonNode seenByCustomer = get("/api/public/orders/" + order.get("publicToken").asText(), null).getBody();
        Assertions.assertThat(seenByCustomer.get("status").asText()).isEqualTo("PAID");
    }

    // The live stream is cut right after it opens, so this screen keeps showing
    // the order as new after another screen has cancelled it.
    @Test
    void kitchenScreenWithAStaleOrderShowsTheConflictAndRefreshes() {
        Restaurant r = openRestaurant("Pho Corner");
        String id = placeOrder(r).get("id").asText();
        context.route("**/api/admin/orders/stream", route -> route.fulfill(new Route.FulfillOptions()
                .setStatus(200).setContentType("text/event-stream").setBody(": connected\n\n")));
        Page page = staffPage(r, "/admin/kitchen");
        assertThat(lane(page, "PLACED").locator(".ticket")).hasCount(1);

        post("/api/admin/orders/" + id + "/transition", Map.of("to", "CANCELLED"), r.owner().cookie());
        ticketButton(page, "Accept").click();

        assertThat(page.locator("#page-error")).not().isEmpty();
        assertThat(page.locator(".ticket")).hasCount(0);
    }

    @Test
    void statusPageFollowsTheKitchen() {
        Restaurant r = openRestaurant("Pho Corner");
        JsonNode order = placeOrder(r);
        Page page = context.newPage();
        page.navigate(base + "/t/" + r.qrToken() + "/orders/" + order.get("publicToken").asText());
        assertThat(page.locator("#status-text")).containsText("sent to the kitchen");

        post("/api/admin/orders/" + order.get("id").asText() + "/transition", Map.of("to", "ACCEPTED"),
                r.owner().cookie());

        assertThat(page.locator("#status-text")).containsText("accepted your order",
                new LocatorAssertions.ContainsTextOptions().setTimeout(15_000));
        assertThat(page.locator("#steps .current")).hasText("Accepted");
    }

    @Test
    void ownerRegistersBuildsAMenuAndPrintsATableCode() {
        String email = uniqueEmail();
        Page page = context.newPage();
        page.navigate(base + "/register");
        page.getByLabel("Restaurant name").fill("Quan Moi");
        page.getByLabel("Email").fill(email);
        page.getByLabel("Password").fill(PASSWORD);
        page.getByText("Create account").click();
        page.waitForURL("**/admin/menu");
        assertThat(page.locator("#who")).hasText(email);

        page.getByLabel("New category name").fill("Noodles");
        page.getByText("Add category").click();
        assertThat(page.locator(".category h2")).hasText("Noodles");

        page.getByText("Add item").click();
        page.locator("#item-name").fill("Pho Bo");
        page.locator("#item-price").fill("60000");
        page.locator("#add-group").click();
        Locator group = page.locator("#groups .group");
        group.locator(".group-head [data-field=name]").fill("Size");
        group.locator("[data-field=minSelect]").fill("1");
        group.locator(".option-row [data-field=name]").fill("Regular");
        group.locator("> button").click();
        group.locator(".option-row").nth(1).locator("[data-field=name]").fill("Large");
        group.locator(".option-row").nth(1).locator("[data-field=priceDelta]").fill("15000");
        page.getByText("Save item").click();
        assertThat(page.locator(".item")).containsText("Pho Bo");
        assertThat(page.locator(".item-options")).hasText("Size: Regular, Large");

        page.navigate(base + "/admin/tables");
        page.getByLabel("New table label").fill("T1");
        page.getByText("Add table").click();
        assertThat(page.locator(".table-card h2")).hasText("T1");
        page.waitForFunction("document.querySelector('img.qr')?.naturalWidth > 0");
        String customerPath = URI.create(page.locator(".table-url").getAttribute("href")).getPath();

        page.navigate(base + "/admin/tables/print");
        assertThat(page.locator("#print-status")).hasText("1 QR code ready.");
        assertThat(page.locator(".qr-card")).containsText("Quan Moi");
        assertThat(page.locator(".qr-card")).containsText("T1");

        page.navigate(base + customerPath);
        assertThat(page.locator("h1")).hasText("Quan Moi");
        assertThat(dish(page, "Pho Bo")).isVisible();
    }

    @Test
    void anExpiredLoginSendsStaffToTheLoginPage() {
        Restaurant r = openRestaurant("Pho Corner");
        Page page = staffPage(r, "/admin/menu");
        assertThat(page.locator("#who")).hasText(r.owner().email());

        context.clearCookies();
        page.getByLabel("New category name").fill("Drinks");
        page.getByText("Add category").click();

        page.waitForURL("**/login");
    }

    @Test
    void markupInNamesAndNotesIsShownAsText() {
        Restaurant r = openRestaurant(MARKUP);
        String categoryId = createCategory(r, MARKUP);
        JsonNode item = post("/api/admin/items", Map.of("categoryId", categoryId, "name", MARKUP,
                "description", MARKUP, "basePrice", 10_000, "sortOrder", 0), r.owner().cookie()).getBody();
        JsonNode table = post("/api/admin/tables", Map.of("label", MARKUP), r.owner().cookie()).getBody();
        String qrToken = URI.create(table.get("url").asText()).getPath().substring("/t/".length());
        JsonNode order = post("/api/public/" + qrToken + "/orders", Map.of("note", MARKUP, "items", List.of(
                Map.of("menuItemId", item.get("id").asText(), "quantity", 1, "note", MARKUP))), null).getBody();

        Page customer = context.newPage();
        customer.navigate(base + "/t/" + qrToken);
        assertThat(customer.locator("h1")).hasText(MARKUP);
        assertThat(dish(customer, MARKUP).locator(".dish-name")).hasText(MARKUP);
        dish(customer, MARKUP).click();
        customer.locator("#item-note").fill(MARKUP);
        customer.locator("#item-form button[type=submit]").click();
        customer.locator("#open-cart").click();
        assertThat(customer.locator("#cart-lines .dish-name")).hasText(MARKUP);
        assertNothingInjected(customer);

        customer.navigate(base + "/t/" + qrToken + "/orders/" + order.get("publicToken").asText());
        assertThat(customer.locator("#note")).containsText(MARKUP);
        assertThat(customer.locator("#lines")).containsText(MARKUP);
        assertNothingInjected(customer);

        Page kitchen = staffPage(r, "/admin/kitchen");
        assertThat(kitchen.locator(".ticket .dish-name")).hasText(MARKUP);
        assertThat(kitchen.locator(".ticket-head")).containsText(MARKUP);
        assertNothingInjected(kitchen);

        Page menu = staffPage(r, "/admin/menu");
        assertThat(menu.locator(".item-name", new Page.LocatorOptions().setHasText(MARKUP))).hasCount(1);
        assertNothingInjected(menu);
    }

    private static void assertNothingInjected(Page page) {
        assertThat(page.locator("img[src=x]")).hasCount(0);
        Assertions.assertThat(page.evaluate("window.__xss")).isNull();
    }

    private Page staffPage(Restaurant r, String path) {
        String token = r.owner().cookie().substring(r.owner().cookie().indexOf('=') + 1);
        context.addCookies(List.of(new Cookie("tabletap_token", token).setUrl(base)));
        Page page = context.newPage();
        page.navigate(base + path);
        return page;
    }

    private static Locator dish(Page page, String name) {
        return page.locator(".dish", new Page.LocatorOptions().setHasText(name));
    }

    private static Locator choice(Page page, String name) {
        return page.locator("label.choice", new Page.LocatorOptions().setHasText(name)).locator("input");
    }

    private static Locator lane(Page page, String status) {
        return page.locator(".lane[data-status=" + status + "]");
    }

    private static Locator ticketButton(Page page, String text) {
        return page.locator(".ticket button", new Page.LocatorOptions().setHasText(text));
    }
}
