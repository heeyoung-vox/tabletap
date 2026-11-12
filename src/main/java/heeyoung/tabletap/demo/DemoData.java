package heeyoung.tabletap.demo;

import heeyoung.tabletap.auth.AuthDtos.RegisterRequest;
import heeyoung.tabletap.auth.AuthService;
import heeyoung.tabletap.floor.TableDtos.TableRequest;
import heeyoung.tabletap.floor.TableLookup;
import heeyoung.tabletap.floor.TableService;
import heeyoung.tabletap.menu.MenuDtos.CategoryRequest;
import heeyoung.tabletap.menu.MenuDtos.ItemRequest;
import heeyoung.tabletap.menu.MenuDtos.ItemResponse;
import heeyoung.tabletap.menu.MenuDtos.OptionGroupRequest;
import heeyoung.tabletap.menu.MenuDtos.OptionRequest;
import heeyoung.tabletap.menu.MenuService;
import heeyoung.tabletap.order.OrderDtos.OrderLine;
import heeyoung.tabletap.order.OrderDtos.PlaceOrderRequest;
import heeyoung.tabletap.order.OrderService;
import heeyoung.tabletap.order.OrderStatus;
import heeyoung.tabletap.tenant.TenantContext;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Two demo restaurants with menus, tables and a few orders, created on startup
 * with the demo profile. A restaurant whose owner already exists is skipped,
 * so restarting the app does not duplicate anything.
 */
@Component
@Profile("demo")
class DemoData implements ApplicationRunner {

    static final String PASSWORD = "tabletap-demo";

    private static final Logger log = LoggerFactory.getLogger(DemoData.class);

    private final AuthService auth;
    private final MenuService menu;
    private final TableService tables;
    private final TableLookup tableLookup;
    private final OrderService orders;
    private final JdbcTemplate jdbc;

    DemoData(AuthService auth, MenuService menu, TableService tables, TableLookup tableLookup, OrderService orders,
            JdbcTemplate jdbc) {
        this.auth = auth;
        this.menu = menu;
        this.tables = tables;
        this.tableLookup = tableLookup;
        this.orders = orders;
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        seed("Phở Góc Phố", "owner@pho.demo", this::phoMenu);
        seed("Bánh Mì Sài Gòn", "owner@banhmi.demo", this::banhMiMenu);
    }

    private void seed(String restaurant, String email, Runnable buildMenu) {
        Integer existing = jdbc.queryForObject("select count(*) from staff_user where email = ?", Integer.class, email);
        if (existing != null && existing > 0) {
            return;
        }
        UUID tenantId = auth.register(new RegisterRequest(restaurant, email, PASSWORD)).tenantId();
        TenantContext.callAs(tenantId, () -> {
            buildMenu.run();
            List<String> qrTokens = new ArrayList<>();
            for (int number = 1; number <= 6; number++) {
                String url = tables.create(new TableRequest("Bàn " + number, true)).url();
                qrTokens.add(url.substring(url.lastIndexOf('/') + 1));
            }
            placeSampleOrders(qrTokens);
            return null;
        });
        log.info("Demo restaurant {} ready. Log in as {} with password {}", restaurant, email, PASSWORD);
    }

    private void phoMenu() {
        UUID soups = category("Phở & Bún", 0);
        UUID drinks = category("Đồ uống", 1);
        item(soups, "Phở Bò Tái", "Rare beef noodle soup with fresh herbs", 65_000, 0,
                group("Size", 1, 1, option("Thường", 0), option("Lớn", 15_000)),
                group("Thêm", 0, 3, option("Trứng chần", 5_000), option("Thêm thịt", 25_000), option("Quẩy", 5_000)));
        item(soups, "Phở Gà", "Chicken noodle soup", 60_000, 1,
                group("Size", 1, 1, option("Thường", 0), option("Lớn", 15_000)));
        item(soups, "Bún Chả Hà Nội", "Grilled pork with rice vermicelli and dipping sauce", 70_000, 2);
        item(drinks, "Cà Phê Sữa Đá", "Vietnamese iced coffee with condensed milk", 30_000, 0,
                group("Đường", 0, 1, option("Ít ngọt", 0), option("Không đường", 0)));
        item(drinks, "Trà Đá", "Iced tea", 5_000, 1);
        item(drinks, "Nước Mía", "Fresh sugarcane juice", 20_000, 2,
                group("Thêm", 0, 1, option("Tắc", 0)));
    }

    private void banhMiMenu() {
        UUID sandwiches = category("Bánh Mì", 0);
        UUID sides = category("Ăn kèm", 1);
        item(sandwiches, "Bánh Mì Thịt", "Pork, pâté, pickles and coriander", 35_000, 0,
                group("Ớt", 1, 1, option("Không cay", 0), option("Cay vừa", 0), option("Rất cay", 0)),
                group("Thêm", 0, 2, option("Trứng ốp la", 8_000), option("Thêm pa-tê", 5_000)));
        item(sandwiches, "Bánh Mì Gà Nướng", "Grilled lemongrass chicken", 40_000, 1,
                group("Ớt", 1, 1, option("Không cay", 0), option("Cay", 0)));
        item(sandwiches, "Bánh Mì Chay", "Tofu and mushroom, vegetarian", 30_000, 2);
        item(sides, "Chả Giò", "Crispy spring rolls, 4 pieces", 45_000, 0);
        item(sides, "Sinh Tố Bơ", "Avocado smoothie", 35_000, 1);
    }

    private void placeSampleOrders(List<String> qrTokens) {
        var items = menu.customerMenu().items();
        record Sample(int table, OrderStatus status, int itemIndex, int quantity) {
        }
        for (Sample sample : List.of(
                new Sample(0, OrderStatus.PLACED, 0, 2),
                new Sample(2, OrderStatus.PREPARING, 1, 1),
                new Sample(4, OrderStatus.SERVED, 2, 3))) {
            var item = items.get(sample.itemIndex());
            List<UUID> options = item.optionGroups().stream()
                    .filter(group -> group.minSelect() > 0)
                    .flatMap(group -> group.options().stream().limit(group.minSelect()))
                    .map(option -> option.id())
                    .toList();
            var table = tableLookup.findActive(qrTokens.get(sample.table())).orElseThrow();
            var placed = orders.place(table, new PlaceOrderRequest(null,
                    List.of(new OrderLine(item.id(), sample.quantity(), options, null))));
            for (OrderStatus step : List.of(OrderStatus.ACCEPTED, OrderStatus.PREPARING, OrderStatus.SERVED)) {
                if (step.ordinal() > sample.status().ordinal()) {
                    break;
                }
                orders.transition(placed.id(), step);
            }
        }
    }

    private UUID category(String name, int sortOrder) {
        return menu.createCategory(new CategoryRequest(name, sortOrder)).id();
    }

    private ItemResponse item(UUID categoryId, String name, String description, long price, int sortOrder,
            OptionGroupRequest... groups) {
        return menu.createItem(new ItemRequest(categoryId, name, description, price, null, true, sortOrder,
                List.of(groups)));
    }

    private static OptionGroupRequest group(String name, int min, int max, OptionRequest... options) {
        return new OptionGroupRequest(name, min, max, List.of(options));
    }

    private static OptionRequest option(String name, long priceDelta) {
        return new OptionRequest(name, priceDelta, true);
    }
}
