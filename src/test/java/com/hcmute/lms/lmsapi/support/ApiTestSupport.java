package com.hcmute.lms.lmsapi.support;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hcmute.lms.lmsapi.dto.LoginRequest;
import com.jayway.jsonpath.JsonPath;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

/**
 * Hàm dùng chung cho test API (MockMvc), tách ra để các lớp test không sao chép cùng một đoạn mã
 * (đo bằng PMD CPD, xem doc/quality/README.md). Chỉ chứa thao tác lặp lại, không chứa phép kiểm
 * nghiệp vụ riêng của từng test.
 */
public final class ApiTestSupport {

  private static final String LOGIN_PATH = "/api/v1/auth/login";

  private ApiTestSupport() {}

  /** Đăng nhập bằng tài khoản thử nghiệm, kỳ vọng HTTP 200 và trả về access token. */
  public static String obtainAccessToken(
      MockMvc mockMvc, ObjectMapper objectMapper, String username, String password)
      throws Exception {
    LoginRequest request = new LoginRequest(username, password);
    MvcResult result =
        mockMvc
            .perform(
                post(LOGIN_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andReturn();

    return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
  }

  /** Kỳ vọng HTTP 403 với thân lỗi chuẩn {@code code = FORBIDDEN}, {@code status = 403}. */
  public static void expectForbidden(MockMvc mockMvc, RequestBuilder request) throws Exception {
    mockMvc
        .perform(request)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"))
        .andExpect(jsonPath("$.status").value(403));
  }

  /**
   * Gửi request có thân JSON kèm {@code Authorization: Bearer <token>}. Trả về {@link
   * ResultActions} để test tự gắn các phép kiểm tra riêng.
   */
  public static ResultActions performJson(
      MockMvc mockMvc,
      ObjectMapper objectMapper,
      MockHttpServletRequestBuilder request,
      String token,
      Object body)
      throws Exception {
    return mockMvc.perform(
        request
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)));
  }

  /** Kỳ vọng HTTP 422 {@code BUSINESS_RULE_VIOLATION} và thông báo chứa {@code messagePart}. */
  public static ResultActions expectBusinessRuleViolation(ResultActions actions, String messagePart)
      throws Exception {
    return actions
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.status").value(422))
        .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
        .andExpect(jsonPath("$.message").value(containsString(messagePart)));
  }

  /** Kỳ vọng HTTP 409 {@code CONFLICT}. */
  public static ResultActions expectConflict(ResultActions actions) throws Exception {
    return actions
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.code").value("CONFLICT"));
  }
}
