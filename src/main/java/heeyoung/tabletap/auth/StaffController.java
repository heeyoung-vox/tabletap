package heeyoung.tabletap.auth;

import heeyoung.tabletap.auth.AuthDtos.StaffResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class StaffController {

    private final StaffService staffService;

    public StaffController(StaffService staffService) {
        this.staffService = staffService;
    }

    @GetMapping("/me")
    StaffResponse me(@AuthenticationPrincipal Jwt jwt) {
        return staffService.get(UUID.fromString(jwt.getSubject()));
    }

    @GetMapping("/staff")
    List<StaffResponse> list() {
        return staffService.list();
    }

    @GetMapping("/staff/{id}")
    StaffResponse get(@PathVariable UUID id) {
        return staffService.get(id);
    }
}
