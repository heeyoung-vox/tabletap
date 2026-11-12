package heeyoung.tabletap.floor;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiningTableRepository extends JpaRepository<DiningTable, UUID> {

    Optional<DiningTable> findByLabel(String label);
}
