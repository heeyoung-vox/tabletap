package heeyoung.tabletap.order;

import heeyoung.tabletap.order.OrderPricing.PricedOption;
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
@Table(name = "order_item_option")
public class OrderItemOption {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_item_id", nullable = false, updatable = false)
    private OrderItem orderItem;

    @Column(nullable = false, updatable = false)
    private int position;

    @Column(name = "group_name", nullable = false, updatable = false)
    private String groupName;

    @Column(name = "option_name", nullable = false, updatable = false)
    private String optionName;

    @Column(name = "price_delta", nullable = false, updatable = false)
    private long priceDelta;

    protected OrderItemOption() {
    }

    OrderItemOption(OrderItem orderItem, int position, PricedOption option) {
        this.orderItem = orderItem;
        this.position = position;
        this.groupName = option.groupName();
        this.optionName = option.optionName();
        this.priceDelta = option.priceDelta();
    }

    public String getGroupName() {
        return groupName;
    }

    public String getOptionName() {
        return optionName;
    }

    public long getPriceDelta() {
        return priceDelta;
    }
}
