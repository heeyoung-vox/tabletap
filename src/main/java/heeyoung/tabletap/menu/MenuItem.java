package heeyoung.tabletap.menu;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.TenantId;

@Entity
@Table(name = "menu_item")
public class MenuItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "base_price", nullable = false)
    private long basePrice;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(nullable = false)
    private boolean available = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @OneToMany(mappedBy = "menuItem", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder")
    private List<OptionGroup> optionGroups = new ArrayList<>();

    protected MenuItem() {
    }

    public MenuItem(Category category, String name, long basePrice) {
        this.category = category;
        this.name = name;
        this.basePrice = basePrice;
    }

    public void update(
            Category category, String name, String description, long basePrice,
            String imageUrl, boolean available, int sortOrder) {
        this.category = category;
        this.name = name;
        this.description = description;
        this.basePrice = basePrice;
        this.imageUrl = imageUrl;
        this.available = available;
        this.sortOrder = sortOrder;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public void clearOptionGroups() {
        optionGroups.clear();
    }

    public OptionGroup addOptionGroup(String name, int minSelect, int maxSelect) {
        var group = new OptionGroup(this, name, minSelect, maxSelect, optionGroups.size());
        optionGroups.add(group);
        return group;
    }

    public UUID getId() {
        return id;
    }

    public Category getCategory() {
        return category;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public long getBasePrice() {
        return basePrice;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public boolean isAvailable() {
        return available;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public List<OptionGroup> getOptionGroups() {
        return List.copyOf(optionGroups);
    }
}
