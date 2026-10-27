package com.hcmute.lms.lmsapi.config;

import com.hcmute.lms.lmsapi.dto.ErrorResponse;
import com.hcmute.lms.lmsapi.exception.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Cấu hình bảo mật Spring Security cho hệ thống LMS-API.
 * Thiết kế cho Stateless REST API, cho phép công khai Actuator Health/Info (US-39).
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
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Vô hiệu hóa CSRF vì API sử dụng JWT/Bearer Token không trạng thái
                .csrf(AbstractHttpConfigurer::disable)

                // Quản lý phiên không lưu trạng thái (Stateless)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Phân quyền truy cập tài nguyên
                .authorizeHttpRequests(authorize -> authorize
                        // Công khai endpoint Actuator Health và Info cho giám sát hệ thống (US-39)
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/actuator/info").permitAll()

                        // Mọi yêu cầu khác đều cần xác thực (Fail-Secure)
                        .anyRequest().authenticated()
                )

                // Tùy biến xử lý lỗi 401 & 403 tại tầng Filter Chain tuân thủ ErrorResponse
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler())
                );

        return http.build();
    }

    /**
     * Xử lý phản hồi khi người dùng chưa xác thực (HTTP 401 Unauthorized) tại tầng Filter Chain.
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
}
