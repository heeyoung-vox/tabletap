package heeyoung.tabletap.floor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class TableDtos {

    private TableDtos() {
    }

    public record TableRequest(@NotBlank @Size(max = 40) String label, Boolean active) {

        public TableRequest {
            active = active == null || active;
        }
    }

    public record TableResponse(UUID id, String label, boolean active, String url) {
    }
}
