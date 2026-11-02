package com.hcmute.lms.lmsapi.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filter chặn mọi HTTP request để quản lý Correlation ID cho truy vết phân tán (US-39). - Nhận hoặc
 * tự sinh UUID định danh duy nhất cho mỗi lượt gọi API. - Đẩy vào SLF4J MDC để tự động xuất hiện
 * trong mọi dòng log của request. - Gắn vào HTTP Response Header X-Correlation-Id để Frontend đối
 * soát. - Dọn dẹp MDC trong khối finally tránh rò rỉ bộ nhớ luồng (ThreadLocal pollution).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

  public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
  public static final String MDC_KEY = "correlationId";

  private static final int MAX_CORRELATION_ID_LENGTH = 128;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String correlationId = safeCopyOrNull(request.getHeader(CORRELATION_ID_HEADER));
    if (correlationId == null) {
      correlationId = UUID.randomUUID().toString();
    }

    MDC.put(MDC_KEY, correlationId);
    response.setHeader(CORRELATION_ID_HEADER, correlationId);

    try {
      filterChain.doFilter(request, response);
    } finally {
      MDC.remove(MDC_KEY);
      MDC.clear();
    }
  }

  /**
   * Chỉ chấp nhận ID do client gửi nếu gồm chữ và số ASCII, `.`, `_`, `-` và dài 1..128 ký tự (sau
   * khi trim). Giá trị khác (có CR/LF, khoảng trắng, ký tự lạ) trả về null để thay bằng UUID mới,
   * vì giá trị này được ghi lại vào response header và log (chống HTTP response splitting/log
   * injection). Kết quả là bản sao dựng từng ký tự đã kiểm tra, không phải chính chuỗi đầu vào.
   */
  private static String safeCopyOrNull(String incoming) {
    if (incoming == null) {
      return null;
    }
    String trimmed = incoming.trim();
    if (trimmed.isEmpty() || trimmed.length() > MAX_CORRELATION_ID_LENGTH) {
      return null;
    }
    StringBuilder copy = new StringBuilder(trimmed.length());
    for (int i = 0; i < trimmed.length(); i++) {
      char c = trimmed.charAt(i);
      if (!isAllowedCorrelationIdChar(c)) {
        return null;
      }
      copy.append(c);
    }
    return copy.toString();
  }

  private static boolean isAllowedCorrelationIdChar(char c) {
    return (c >= 'a' && c <= 'z')
        || (c >= 'A' && c <= 'Z')
        || (c >= '0' && c <= '9')
        || c == '.'
        || c == '_'
        || c == '-';
  }
}
