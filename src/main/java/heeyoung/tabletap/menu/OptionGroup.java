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
@Table(name = "option_group")
public class OptionGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "menu_item_id", nullable = false)
    private MenuItem menuItem;

    @Column(nullable = false)
    private String name;

    @Column(name = "min_select", nullable = false)
    private int minSelect;

    @Column(name = "max_select", nullable = false)
    private int maxSelect;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @OneToMany(mappedBy = "optionGroup", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder")
    private List<MenuOption> options = new ArrayList<>();

    protected OptionGroup() {
    }

    OptionGroup(MenuItem menuItem, String name, int minSelect, int maxSelect, int sortOrder) {
        this.menuItem = menuItem;
        this.name = name;
        this.minSelect = minSelect;
        this.maxSelect = maxSelect;
        this.sortOrder = sortOrder;
    }

    public void addOption(String name, long priceDelta, boolean available) {
        options.add(new MenuOption(this, name, priceDelta, available, options.size()));
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getMinSelect() {
        return minSelect;
    }

    public int getMaxSelect() {
        return maxSelect;
    }

    public List<MenuOption> getOptions() {
        return List.copyOf(options);
    }
}
