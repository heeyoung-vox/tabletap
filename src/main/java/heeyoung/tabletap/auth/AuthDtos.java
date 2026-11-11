package heeyoung.tabletap.auth;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {
    }

    // BCrypt hashes at most 72 bytes, and a non-ASCII character takes more than one.
    static final int MAX_PASSWORD_BYTES = 72;

    static boolean fitsBcrypt(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length <= MAX_PASSWORD_BYTES;
    }

    public record RegisterRequest(
            @NotBlank @Size(max = 120) String restaurantName,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 8) String password) {

        @AssertTrue(message = "password must be at most 72 bytes")
        boolean isPasswordWithinLimit() {
            return password == null || fitsBcrypt(password);
        }
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    public record StaffResponse(UUID id, String email, StaffRole role, UUID tenantId) {

        static StaffResponse from(StaffUser user) {
            return new StaffResponse(user.getId(), user.getEmail(), user.getRole(), user.getTenantId());
        }
    }
}
