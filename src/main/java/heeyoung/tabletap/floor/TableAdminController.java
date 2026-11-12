package heeyoung.tabletap.floor;

import heeyoung.tabletap.floor.TableDtos.TableRequest;
import heeyoung.tabletap.floor.TableDtos.TableResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/tables")
public class TableAdminController {

    private final TableService tables;

    public TableAdminController(TableService tables) {
        this.tables = tables;
    }

    @GetMapping
    List<TableResponse> list() {
        return tables.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    TableResponse create(@Valid @RequestBody TableRequest request) {
        return tables.create(request);
    }

    @PutMapping("/{id}")
    TableResponse update(@PathVariable UUID id, @Valid @RequestBody TableRequest request) {
        return tables.update(id, request);
    }

    @PostMapping("/{id}/regenerate-qr")
    TableResponse regenerateQr(@PathVariable UUID id) {
        return tables.regenerateQr(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) {
        tables.delete(id);
    }

    // Not cached: after a regenerate the same URL must return the new code.
    @GetMapping(value = "/{id}/qr.png", produces = MediaType.IMAGE_PNG_VALUE)
    ResponseEntity<byte[]> qrPng(@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(tables.qrPng(id));
    }
}
