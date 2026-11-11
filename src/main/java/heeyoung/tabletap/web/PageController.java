package heeyoung.tabletap.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

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
}
