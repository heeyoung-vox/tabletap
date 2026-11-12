package heeyoung.tabletap.floor;

import heeyoung.tabletap.floor.TableDtos.TableRequest;
import heeyoung.tabletap.floor.TableDtos.TableResponse;
import java.text.Normalizer;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class TableService {

    private final DiningTableRepository tables;
    private final String baseUrl;

    public TableService(DiningTableRepository tables, @Value("${tabletap.base-url}") String baseUrl) {
        this.tables = tables;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    @Transactional(readOnly = true)
    public List<TableResponse> list() {
        return tables.findAll(Sort.by("label")).stream().map(this::response).toList();
    }

    public TableResponse create(TableRequest request) {
        String label = clean(request.label());
        requireLabelFree(label, null);
        DiningTable table = new DiningTable(label);
        table.update(label, request.active());
        return response(tables.save(table));
    }

    public TableResponse update(UUID id, TableRequest request) {
        DiningTable table = table(id);
        String label = clean(request.label());
        requireLabelFree(label, id);
        table.update(label, request.active());
        return response(table);
    }

    public TableResponse regenerateQr(UUID id) {
        DiningTable table = table(id);
        table.regenerateQrToken();
        return response(table);
    }

    public void delete(UUID id) {
        tables.delete(table(id));
    }

    @Transactional(readOnly = true)
    public byte[] qrPng(UUID id) {
        return QrCodes.png(customerUrl(table(id)));
    }

    // The database has a unique constraint too. This check exists to give a
    // clear message in the normal case.
    private void requireLabelFree(String label, UUID ownId) {
        tables.findByLabel(label)
                .filter(existing -> !existing.getId().equals(ownId))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "A table with this label already exists");
                });
    }

    // The same accented label can be typed as different byte sequences. Storing
    // one form keeps the uniqueness check honest.
    private static String clean(String label) {
        return Normalizer.normalize(label, Normalizer.Form.NFC).strip();
    }

    private TableResponse response(DiningTable table) {
        return new TableResponse(table.getId(), table.getLabel(), table.isActive(), customerUrl(table));
    }

    private String customerUrl(DiningTable table) {
        return baseUrl + "/t/" + table.getQrToken();
    }

    private DiningTable table(UUID id) {
        return tables.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Table not found"));
    }
}
