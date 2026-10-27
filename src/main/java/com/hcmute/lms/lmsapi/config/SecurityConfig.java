package com.hcmute.lms.lmsapi.config;

import com.hcmute.lms.lmsapi.dto.ErrorResponse;
import com.hcmute.lms.lmsapi.dto.UserPrincipal;
import com.hcmute.lms.lmsapi.exception.ErrorCode;
import com.hcmute.lms.lmsapi.service.AuthSessionService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

/**
 * Cấu hình bảo mật Spring Security cho hệ thống LMS-API (F01 / ADR 0002 / ADR 0003).
 * Thiết kế cho Stateless REST API, tích hợp Nimbus OAuth2 Resource Server, CORS và phân quyền động.
 */
@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final ObjectMapper objectMapper;

    /**
     * Cấu hình SecurityFilterChain chính.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CorsConfigurationSource corsConfigurationSource,
            BearerTokenResolver bearerTokenResolver,
            Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter) throws Exception {
        http
                // Cấu hình CORS an toàn theo quy định ADR 0003
                .cors(cors -> cors.configurationSource(corsConfigurationSource))

                // Vô hiệu hóa CSRF vì API sử dụng JWT/Bearer Token không trạng thái
                .csrf(AbstractHttpConfigurer::disable)

                // Quản lý phiên không lưu trạng thái (Stateless)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Phân quyền truy cập tài nguyên
                .authorizeHttpRequests(authorize -> authorize
                        // Cho phép preflight request OPTIONS đi qua không cần xác thực
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // Công khai endpoint đăng nhập hệ thống (US-01 / ADR 0003)
                        .requestMatchers("/api/v1/auth/login").permitAll()

                        // Công khai endpoint Actuator Health và Info cho giám sát hệ thống (US-39)
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/actuator/info").permitAll()

                        // Phân hệ quản lý người dùng chỉ dành riêng cho ADMIN (US-02 / ADR 0003)
                        .requestMatchers("/api/v1/users", "/api/v1/users/**").hasRole("ADMIN")

                        // Mọi yêu cầu khác đều cần xác thực (Fail-Secure)
                        .anyRequest().authenticated()
                )

                // Cấu hình OAuth2 Resource Server với Nimbus JWT (ADR 0003)
                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenResolver(bearerTokenResolver)
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                )

                // Tùy biến xử lý lỗi 401 & 403 tại tầng Filter Chain tuân thủ ErrorResponse
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler())
                );

        return http.build();
    }

    /**
     * Bộ giải quyết Bearer Token tùy biến: bỏ qua kiểm tra token trên các endpoint công khai (permitAll).
     * Giúp POST /api/v1/auth/login thành công bình thường ngay cả khi client gửi kèm token cũ hết hạn trên header.
     */
    @Bean
    public BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver defaultResolver = new DefaultBearerTokenResolver();
        return request -> {
            if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
                return null;
            }
            String uri = request.getRequestURI();
            if (isPermitAllUri(uri)) {
                return null;
            }
            return defaultResolver.resolve(request);
        };
    }

    private static boolean isPermitAllUri(String uri) {
        if (uri == null) {
            return false;
        }
        String clean = uri.endsWith("/") && uri.length() > 1 ? uri.substring(0, uri.length() - 1) : uri;
        if ("/api/v1/auth/login".equals(clean)) {
            return true;
        }
        if ("/actuator/info".equals(clean)) {
            return true;
        }
        if ("/actuator/health".equals(clean) || clean.startsWith("/actuator/health/")) {
            return true;
        }
        return false;
    }

    /**
     * Chuyển đổi JWT đã giải mã thành Authentication chứa UserPrincipal thông qua AuthSessionService.
     */
    @Bean
    public Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter(
            AuthSessionService authSessionService) {
        return jwt -> {
            UserPrincipal principal = authSessionService.validateUserSession(jwt);
            return new UsernamePasswordAuthenticationToken(principal, jwt, principal.getAuthorities());
        };
    }

    /**
     * Cấu hình chia sẻ tài nguyên nguồn gốc chéo (CORS) theo ADR 0003.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:http://localhost:4200,http://localhost:3000}") List<String> allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("X-Correlation-Id"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * Xử lý phản hồi khi người dùng chưa xác thực hoặc token không hợp lệ (HTTP 401 Unauthorized).
     */
    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> {
            log.warn("Unauthorized access attempt to [{}]: {}", request.getRequestURI(), authException.getMessage());

            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());

            ErrorResponse errorResponse = ErrorResponse.builder()
                    .timestamp(Instant.now())
                    .status(HttpStatus.UNAUTHORIZED.value())
                    .code(ErrorCode.UNAUTHORIZED)
                    .message("Yêu cầu xác thực để truy cập tài nguyên này")
                    .path(request.getRequestURI())
                    .build();

            response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
        };
    }

    /**
     * Xử lý phản hồi khi người dùng không đủ quyền hạn (HTTP 403 Forbidden) tại tầng Filter Chain.
     */
    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            log.warn("Forbidden access attempt to [{}]: {}", request.getRequestURI(), accessDeniedException.getMessage());

            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());

            ErrorResponse errorResponse = ErrorResponse.builder()
                    .timestamp(Instant.now())
                    .status(HttpStatus.FORBIDDEN.value())
                    .code(ErrorCode.FORBIDDEN)
                    .message("Bạn không có quyền truy cập tài nguyên này")
                    .path(request.getRequestURI())
                    .build();

            response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
        };
    }

    /**
     * Bean mã hóa mật khẩu sử dụng thuật toán BCrypt (F01 / ADR 0003).
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
