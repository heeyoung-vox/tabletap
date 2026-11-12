package heeyoung.tabletap.order;

import heeyoung.tabletap.order.OrderPricing.PricedOrder;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.TenantId;

// "Order" is a JPQL keyword, hence the longer name.
@Entity
@Table(name = "orders")
public class CustomerOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "dining_table_id", updatable = false)
    private UUID diningTableId;

    @Column(name = "table_label", nullable = false, updatable = false)
    private String tableLabel;

    // Lets the customer follow the order without an account.
    @Column(name = "public_token", nullable = false, unique = true, updatable = false)
    private UUID publicToken = UUID.randomUUID();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.PLACED;

    @Column(nullable = false, updatable = false)
    private long total;

    @Column(updatable = false)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = createdAt;

    // Two staff moving the same order at once: the second update fails instead
    // of silently overwriting the first.
    @Version
    private long version;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<OrderItem> items = new ArrayList<>();

    protected CustomerOrder() {
    }

    public CustomerOrder(UUID diningTableId, String tableLabel, String note, PricedOrder priced) {
        this.diningTableId = diningTableId;
        this.tableLabel = tableLabel;
        this.note = note;
        this.total = priced.total();
        for (var line : priced.lines()) {
            items.add(new OrderItem(this, items.size(), line));
        }
    }

    public void moveTo(OrderStatus target) {
        if (!status.canMoveTo(target)) {
            throw new IllegalOrderTransitionException(status, target);
        }
        status = target;
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getPublicToken() {
        return publicToken;
    }

    public String getTableLabel() {
        return tableLabel;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public long getTotal() {
        return total;
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<OrderItem> getItems() {
        return List.copyOf(items);
    }
}
