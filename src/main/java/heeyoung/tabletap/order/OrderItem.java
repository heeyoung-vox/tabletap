package heeyoung.tabletap.order;

import heeyoung.tabletap.order.OrderPricing.PricedLine;
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

// Copies names and prices at order time, so later menu edits never change a past order.
@Entity
@Table(name = "order_item")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private CustomerOrder order;

    @Column(nullable = false, updatable = false)
    private int position;

    @Column(name = "menu_item_id", updatable = false)
    private UUID menuItemId;

    @Column(name = "item_name", nullable = false, updatable = false)
    private String itemName;

    @Column(name = "unit_price", nullable = false, updatable = false)
    private long unitPrice;

    @Column(nullable = false, updatable = false)
    private int quantity;

    @Column(name = "line_total", nullable = false, updatable = false)
    private long lineTotal;

    @Column(updatable = false)
    private String note;

    @OneToMany(mappedBy = "orderItem", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<OrderItemOption> options = new ArrayList<>();

    protected OrderItem() {
    }

    OrderItem(CustomerOrder order, int position, PricedLine line) {
        this.order = order;
        this.position = position;
        this.menuItemId = line.menuItemId();
        this.itemName = line.itemName();
        this.unitPrice = line.unitPrice();
        this.quantity = line.quantity();
        this.lineTotal = line.lineTotal();
        this.note = line.note();
        for (var option : line.options()) {
            options.add(new OrderItemOption(this, options.size(), option));
        }
    }

    public String getItemName() {
        return itemName;
    }

    public long getUnitPrice() {
        return unitPrice;
    }

    public int getQuantity() {
        return quantity;
    }

    public long getLineTotal() {
        return lineTotal;
    }

    public String getNote() {
        return note;
    }

    public List<OrderItemOption> getOptions() {
        return List.copyOf(options);
    }
}
