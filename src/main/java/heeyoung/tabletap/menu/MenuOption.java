package heeyoung.tabletap.menu;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.TenantId;

@Entity
@Table(name = "menu_option")
public class MenuOption {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "option_group_id", nullable = false)
    private OptionGroup optionGroup;

    @Column(nullable = false)
    private String name;

    @Column(name = "price_delta", nullable = false)
    private long priceDelta;

    @Column(nullable = false)
    private boolean available;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected MenuOption() {
    }

    MenuOption(OptionGroup optionGroup, String name, long priceDelta, boolean available, int sortOrder) {
        this.optionGroup = optionGroup;
        this.name = name;
        this.priceDelta = priceDelta;
        this.available = available;
        this.sortOrder = sortOrder;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public long getPriceDelta() {
        return priceDelta;
    }

    public boolean isAvailable() {
        return available;
    }
}
