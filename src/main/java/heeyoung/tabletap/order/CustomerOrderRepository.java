package heeyoung.tabletap.order;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, UUID> {

    Optional<CustomerOrder> findByPublicToken(UUID publicToken);

    List<CustomerOrder> findBy(Sort sort, Limit limit);

    List<CustomerOrder> findByStatusIn(Collection<OrderStatus> statuses, Sort sort, Limit limit);
}
