package heeyoung.tabletap.auth;

import heeyoung.tabletap.auth.AuthDtos.LoginRequest;
import heeyoung.tabletap.auth.AuthDtos.RegisterRequest;
import heeyoung.tabletap.auth.AuthDtos.StaffResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final TokenService tokenService;

    public AuthController(AuthService authService, TokenService tokenService) {
        this.authService = authService;
        this.tokenService = tokenService;
    }

    @PostMapping("/register")
    ResponseEntity<StaffResponse> register(@Valid @RequestBody RegisterRequest request) {
        return withLoginCookie(HttpStatus.CREATED, authService.register(request));
    }

    @PostMapping("/login")
    ResponseEntity<StaffResponse> login(@Valid @RequestBody LoginRequest request) {
        return withLoginCookie(HttpStatus.OK, authService.login(request));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, tokenService.logoutCookie().toString())
                .build();
    }

    private ResponseEntity<StaffResponse> withLoginCookie(HttpStatus status, StaffResponse user) {
        var cookie = tokenService.loginCookie(user.id(), user.tenantId(), user.role());
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(user);
    }
}
