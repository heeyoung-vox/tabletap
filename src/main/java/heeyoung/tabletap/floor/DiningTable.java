package heeyoung.tabletap.floor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.TenantId;

@Entity
@Table(name = "dining_table")
public class DiningTable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String label;

    // Printed inside the QR code. Random and unguessable, and replaced when the
    // owner regenerates the code, which makes the old printout stop working.
    @Column(name = "qr_token", nullable = false, unique = true)
    private UUID qrToken = UUID.randomUUID();

    @Column(nullable = false)
    private boolean active = true;

    protected DiningTable() {
    }

    public DiningTable(String label) {
        this.label = label;
    }

    public void update(String label, boolean active) {
        this.label = label;
        this.active = active;
    }

    public void regenerateQrToken() {
        this.qrToken = UUID.randomUUID();
    }

    public UUID getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public UUID getQrToken() {
        return qrToken;
    }

    public boolean isActive() {
        return active;
    }
}
