package heeyoung.tabletap.auth;

import heeyoung.tabletap.auth.AuthDtos.StaffResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class StaffService {

    private final StaffUserRepository staffUsers;

    public StaffService(StaffUserRepository staffUsers) {
        this.staffUsers = staffUsers;
    }

    public List<StaffResponse> list() {
        return staffUsers.findAll(Sort.by("email")).stream().map(StaffResponse::from).toList();
    }

    public StaffResponse get(UUID id) {
        return staffUsers.findById(id)
                .map(StaffResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Staff user not found"));
    }
}
