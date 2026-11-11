package heeyoung.tabletap.menu;

import heeyoung.tabletap.menu.MenuDtos.AvailabilityRequest;
import heeyoung.tabletap.menu.MenuDtos.CategoryRequest;
import heeyoung.tabletap.menu.MenuDtos.CategoryResponse;
import heeyoung.tabletap.menu.MenuDtos.ItemRequest;
import heeyoung.tabletap.menu.MenuDtos.ItemResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class MenuAdminController {

    private final MenuService menu;

    public MenuAdminController(MenuService menu) {
        this.menu = menu;
    }

    @GetMapping("/categories")
    List<CategoryResponse> listCategories() {
        return menu.listCategories();
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    CategoryResponse createCategory(@Valid @RequestBody CategoryRequest request) {
        return menu.createCategory(request);
    }

    @PutMapping("/categories/{id}")
    CategoryResponse updateCategory(@PathVariable UUID id, @Valid @RequestBody CategoryRequest request) {
        return menu.updateCategory(id, request);
    }

    @DeleteMapping("/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteCategory(@PathVariable UUID id) {
        menu.deleteCategory(id);
    }

    @GetMapping("/items")
    List<ItemResponse> listItems() {
        return menu.listItems();
    }

    @GetMapping("/items/{id}")
    ItemResponse getItem(@PathVariable UUID id) {
        return menu.getItem(id);
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    ItemResponse createItem(@Valid @RequestBody ItemRequest request) {
        return menu.createItem(request);
    }

    @PutMapping("/items/{id}")
    ItemResponse updateItem(@PathVariable UUID id, @Valid @RequestBody ItemRequest request) {
        return menu.updateItem(id, request);
    }

    @PutMapping("/items/{id}/availability")
    ItemResponse setItemAvailable(@PathVariable UUID id, @Valid @RequestBody AvailabilityRequest request) {
        return menu.setItemAvailable(id, request.available());
    }

    @DeleteMapping("/items/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteItem(@PathVariable UUID id) {
        menu.deleteItem(id);
    }
}
