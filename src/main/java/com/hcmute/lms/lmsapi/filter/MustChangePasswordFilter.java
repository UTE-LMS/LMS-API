package com.hcmute.lms.lmsapi.filter;

import com.hcmute.lms.lmsapi.dto.ErrorResponse;
import com.hcmute.lms.lmsapi.dto.UserPrincipal;
import com.hcmute.lms.lmsapi.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Chặn mọi API nghiệp vụ khi tài khoản còn cờ {@code mustChangePassword}. Cờ chỉ ở UI thì người
 * dùng gọi thẳng API bằng token vẫn qua được, nên việc ép đổi mật khẩu phải nằm ở server. Chỉ cho
 * qua: đổi mật khẩu, xem hồ sơ (để UI biết cờ) và đăng xuất.
 *
 * <p>Đăng ký trong {@code SecurityConfig} ngay sau bước xác thực Bearer token (không đánh dấu
 * {@code @Component} để tránh bị đăng ký thêm lần nữa như servlet filter thường).
 */
@Slf4j
@RequiredArgsConstructor
public class MustChangePasswordFilter extends OncePerRequestFilter {

  private static final Set<String> ALLOWED_PATHS =
      Set.of("/api/v1/auth/change-password", "/api/v1/auth/me", "/api/v1/auth/logout");

  private static final String PASSWORD_CHANGE_MESSAGE =
      "Bạn cần đổi mật khẩu trước khi tiếp tục sử dụng hệ thống";

  private final ObjectMapper objectMapper;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null
        && authentication.getPrincipal() instanceof UserPrincipal principal
        && principal.isMustChangePassword()
        && !isAllowed(request)) {
      log.warn(
          "Chặn [{} {}] vì user id={} chưa đổi mật khẩu lần đầu",
          request.getMethod(),
          request.getRequestURI(),
          principal.getId());
      writeForbidden(request, response);
      return;
    }
    filterChain.doFilter(request, response);
  }

  private static boolean isAllowed(HttpServletRequest request) {
    if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
      return true;
    }
    String uri = request.getRequestURI();
    String clean = uri.endsWith("/") && uri.length() > 1 ? uri.substring(0, uri.length() - 1) : uri;
    return ALLOWED_PATHS.contains(clean);
  }

  private void writeForbidden(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());

    ErrorResponse body =
        ErrorResponse.builder()
            .timestamp(Instant.now())
            .status(HttpStatus.FORBIDDEN.value())
            .code(ErrorCode.FORBIDDEN)
            .message(PASSWORD_CHANGE_MESSAGE)
            .path(request.getRequestURI())
            .build();
    response.getWriter().write(objectMapper.writeValueAsString(body));
  }
}
