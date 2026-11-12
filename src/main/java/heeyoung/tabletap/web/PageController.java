package heeyoung.tabletap.web;

import heeyoung.tabletap.floor.TableLookup;
import heeyoung.tabletap.order.OrderLookup;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class PageController {

    private final TableLookup tableLookup;
    private final OrderLookup orderLookup;

    public PageController(TableLookup tableLookup, OrderLookup orderLookup) {
        this.tableLookup = tableLookup;
        this.orderLookup = orderLookup;
    }

    @GetMapping("/")
    String home() {
        return "redirect:/admin/menu";
    }

    @GetMapping("/login")
    String login() {
        return "login";
    }

    @GetMapping("/register")
    String register() {
        return "register";
    }

    @GetMapping("/admin/menu")
    String menu() {
        return "admin/menu";
    }

    @GetMapping("/admin/tables")
    String tables() {
        return "admin/tables";
    }

    @GetMapping("/admin/tables/print")
    String tablesPrint() {
        return "admin/tables-print";
    }

    @GetMapping("/t/{qrToken}")
    String table(@PathVariable String qrToken, Model model) {
        var table = tableLookup.findActive(qrToken)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "This QR code is no longer valid"));
        model.addAttribute("restaurantName", table.restaurantName());
        model.addAttribute("tableLabel", table.tableLabel());
        model.addAttribute("qrToken", qrToken);
        return "table";
    }

    // The page loads the order itself. Checking the token here turns a mistyped
    // link into a 404 instead of an empty page.
    @GetMapping("/t/{qrToken}/orders/{publicToken}")
    String order(@PathVariable String qrToken, @PathVariable String publicToken, Model model) {
        OrderLookup.parse(publicToken).flatMap(orderLookup::tenantOf)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        model.addAttribute("qrToken", qrToken);
        model.addAttribute("publicToken", publicToken);
        return "order";
    }
}
