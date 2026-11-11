package heeyoung.tabletap.menu;

import heeyoung.tabletap.menu.MenuDtos.CategoryRequest;
import heeyoung.tabletap.menu.MenuDtos.CategoryResponse;
import heeyoung.tabletap.menu.MenuDtos.ItemRequest;
import heeyoung.tabletap.menu.MenuDtos.ItemResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

// Every lookup here goes through a tenant-filtered repository, so an id that
// belongs to another tenant is simply not found.
@Service
@Transactional
public class MenuService {

    private static final Sort DISPLAY_ORDER = Sort.by("sortOrder", "name");

    private final CategoryRepository categories;
    private final MenuItemRepository items;

    public MenuService(CategoryRepository categories, MenuItemRepository items) {
        this.categories = categories;
        this.items = items;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> listCategories() {
        return categories.findAll(DISPLAY_ORDER).stream().map(CategoryResponse::from).toList();
    }

    public CategoryResponse createCategory(CategoryRequest request) {
        var category = categories.save(new Category(request.name().strip(), request.sortOrder()));
        return CategoryResponse.from(category);
    }

    public CategoryResponse updateCategory(UUID id, CategoryRequest request) {
        Category category = category(id);
        category.update(request.name().strip(), request.sortOrder());
        return CategoryResponse.from(category);
    }

    public void deleteCategory(UUID id) {
        Category category = category(id);
        if (items.existsByCategoryId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Category still has items");
        }
        categories.delete(category);
    }

    @Transactional(readOnly = true)
    public List<ItemResponse> listItems() {
        return items.findAll(DISPLAY_ORDER).stream().map(ItemResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ItemResponse getItem(UUID id) {
        return ItemResponse.from(item(id));
    }

    public ItemResponse createItem(ItemRequest request) {
        var item = new MenuItem(category(request.categoryId()), request.name().strip(), request.basePrice());
        apply(item, request);
        return ItemResponse.from(items.saveAndFlush(item));
    }

    // Option groups are replaced as a whole, so their ids change on every update.
    public ItemResponse updateItem(UUID id, ItemRequest request) {
        MenuItem item = item(id);
        item.clearOptionGroups();
        // Delete the old groups before inserting the new ones.
        items.flush();
        apply(item, request);
        return ItemResponse.from(items.saveAndFlush(item));
    }

    public ItemResponse setItemAvailable(UUID id, boolean available) {
        MenuItem item = item(id);
        item.setAvailable(available);
        return ItemResponse.from(item);
    }

    public void deleteItem(UUID id) {
        items.delete(item(id));
    }

    private void apply(MenuItem item, ItemRequest request) {
        item.update(
                category(request.categoryId()),
                request.name().strip(),
                request.description(),
                request.basePrice(),
                request.imageUrl(),
                request.available(),
                request.sortOrder());
        for (var groupRequest : request.optionGroups()) {
            OptionGroup group = item.addOptionGroup(
                    groupRequest.name().strip(), groupRequest.minSelect(), groupRequest.maxSelect());
            for (var option : groupRequest.options()) {
                group.addOption(option.name().strip(), option.priceDelta(), option.available());
            }
        }
    }

    private Category category(UUID id) {
        return categories.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
    }

    private MenuItem item(UUID id) {
        return items.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Menu item not found"));
    }
}
