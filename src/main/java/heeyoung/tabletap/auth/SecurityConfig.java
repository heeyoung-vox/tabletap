package heeyoung.tabletap.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import heeyoung.tabletap.tenant.TenantContextFilter;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    private static final String ADMIN_API_PREFIX = "/api/admin/";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // The login cookie is SameSite=Strict, so browsers never attach it to
                // cross-site requests. That covers CSRF for the JSON API.
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**", "/actuator/health", "/error").permitAll()
                        .requestMatchers("/login", "/register", "/css/**", "/js/**").permitAll()
                        .requestMatchers("/", "/api/admin/**", "/admin/**").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint()))
                .oauth2ResourceServer(oauth -> oauth
                        .authenticationEntryPoint(entryPoint())
                        .bearerTokenResolver(cookieTokenResolver())
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(roleConverter())))
                .addFilterAfter(new TenantContextFilter(), BearerTokenAuthenticationFilter.class)
                .build();
    }

    // A browser opening an admin page without a valid login goes to the login
    // page. Everything else, including the API, gets a plain 401.
    private static AuthenticationEntryPoint entryPoint() {
        var api = new BearerTokenAuthenticationEntryPoint();
        var page = new LoginUrlAuthenticationEntryPoint("/login");
        return (request, response, exception) ->
                (isAdminPage(path(request)) ? page : api).commence(request, response, exception);
    }

    // The cookie is read only where a login is required. An expired or forged
    // cookie would otherwise turn public pages such as /login into a 401 and
    // lock the user out until they cleared their cookies.
    private static BearerTokenResolver cookieTokenResolver() {
        return (HttpServletRequest request) -> {
            String path = path(request);
            boolean needsLogin = isAdminPage(path) || path.startsWith(ADMIN_API_PREFIX);
            if (!needsLogin || request.getCookies() == null) {
                return null;
            }
            for (Cookie cookie : request.getCookies()) {
                if (TokenService.COOKIE_NAME.equals(cookie.getName()) && !cookie.getValue().isEmpty()) {
                    return cookie.getValue();
                }
            }
            return null;
        };
    }

    private static String path(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }

    private static boolean isAdminPage(String path) {
        return path.equals("/") || path.equals("/admin") || path.startsWith("/admin/");
    }

    private static JwtAuthenticationConverter roleConverter() {
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt ->
                List.of(new SimpleGrantedAuthority("ROLE_" + jwt.getClaimAsString(TokenService.ROLE_CLAIM))));
        return converter;
    }

    @Bean
    SecretKey jwtSecretKey(JwtProperties properties) {
        return new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
        return NimbusJwtDecoder.withSecretKey(jwtSecretKey).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
