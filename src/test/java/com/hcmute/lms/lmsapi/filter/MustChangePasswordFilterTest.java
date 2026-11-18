package com.hcmute.lms.lmsapi.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.hcmute.lms.lmsapi.dto.UserPrincipal;
import com.hcmute.lms.lmsapi.enums.Role;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;

/** Unit test MustChangePasswordFilter (F01): chặn API nghiệp vụ khi còn cờ mustChangePassword. */
@DisplayName("F01: MustChangePasswordFilter")
class MustChangePasswordFilterTest {

  private final MustChangePasswordFilter filter =
      new MustChangePasswordFilter(JsonMapper.builder().build());

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  private void authenticateAs(boolean mustChangePassword) {
    UserPrincipal principal =
        UserPrincipal.builder()
            .id("64b000000000000000000001")
            .username("gv_test")
            .mustChangePassword(mustChangePassword)
            .roles(List.of(Role.LECTURER))
            .build();
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, "x", principal.getAuthorities()));
  }

  /** Chạy filter và trả về response; chain.getRequest() != null nghĩa là request được cho qua. */
  private MockFilterChain run(String method, String uri, MockHttpServletResponse response)
      throws Exception {
    MockFilterChain chain = new MockFilterChain();
    filter.doFilter(new MockHttpServletRequest(method, uri), response, chain);
    return chain;
  }

  @Test
  @DisplayName("Còn cờ: GET /api/v1/courses bị chặn 403 FORBIDDEN kèm thông báo cần đổi mật khẩu")
  void blocksBusinessEndpoint() throws Exception {
    authenticateAs(true);
    MockHttpServletResponse response = new MockHttpServletResponse();

    MockFilterChain chain = run("GET", "/api/v1/courses", response);

    assertThat(chain.getRequest()).isNull();
    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.getContentAsString()).contains("FORBIDDEN").contains("đổi mật khẩu");
  }

  @Test
  @DisplayName("Còn cờ: POST change-password, POST logout, GET me (kể cả dấu / cuối) được cho qua")
  void allowsAuthEndpoints() throws Exception {
    authenticateAs(true);
    List<String[]> allowed =
        List.of(
            new String[] {"POST", "/api/v1/auth/change-password"},
            new String[] {"POST", "/api/v1/auth/logout"},
            new String[] {"GET", "/api/v1/auth/me"},
            new String[] {"GET", "/api/v1/auth/me/"});
    for (String[] call : allowed) {
      MockHttpServletResponse response = new MockHttpServletResponse();
      MockFilterChain chain = run(call[0], call[1], response);
      assertThat(chain.getRequest()).as(call[0] + " " + call[1]).isNotNull();
      assertThat(response.getStatus()).as(call[0] + " " + call[1]).isEqualTo(200);
    }
  }

  @Test
  @DisplayName("Còn cờ: PUT /auth/me (sửa hồ sơ) bị chặn, chỉ được đọc hồ sơ")
  void blocksProfileUpdate() throws Exception {
    authenticateAs(true);
    MockHttpServletResponse response = new MockHttpServletResponse();

    MockFilterChain chain = run("PUT", "/api/v1/auth/me", response);

    assertThat(chain.getRequest()).isNull();
    assertThat(response.getStatus()).isEqualTo(403);
  }

  @Test
  @DisplayName("Còn cờ: preflight OPTIONS được cho qua để CORS không hỏng")
  void allowsOptions() throws Exception {
    authenticateAs(true);

    MockFilterChain chain = run("OPTIONS", "/api/v1/courses", new MockHttpServletResponse());

    assertThat(chain.getRequest()).isNotNull();
  }

  @Test
  @DisplayName("Đã đổi mật khẩu (cờ tắt): API nghiệp vụ đi qua bình thường")
  void flagOffPassesThrough() throws Exception {
    authenticateAs(false);

    MockFilterChain chain = run("GET", "/api/v1/courses", new MockHttpServletResponse());

    assertThat(chain.getRequest()).isNotNull();
  }

  @Test
  @DisplayName("Chưa xác thực: filter không can thiệp (để Spring Security trả 401)")
  void unauthenticatedPassesThrough() throws Exception {
    MockFilterChain chain = run("GET", "/api/v1/courses", new MockHttpServletResponse());

    assertThat(chain.getRequest()).isNotNull();
  }
}
