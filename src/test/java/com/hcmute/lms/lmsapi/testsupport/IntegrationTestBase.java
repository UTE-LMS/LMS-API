package com.hcmute.lms.lmsapi.testsupport;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.hcmute.lms.lmsapi.config.JwtConfig;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

/**
 * Lớp nền dùng chung cho integration test API (issue #52).
 *
 * <p>Chạy {@code @SpringBootTest} đầy đủ (Security, Jackson, Mongo) trên MongoDB replica set thật
 * lấy từ {@code MONGODB_URI} (xem README và ADR 0008); không dùng Testcontainers (ADR 0002). Mỗi
 * test có một {@link ApiTestFixtures} riêng, tự dọn ở {@code @AfterEach}, nên các test không phụ
 * thuộc thứ tự chạy. Lớp con chỉ khai báo kịch bản, không lặp lại việc dựng MockMvc, token, dọn dữ
 * liệu.
 */
@SpringBootTest
public abstract class IntegrationTestBase {

  @Autowired private WebApplicationContext context;
  @Autowired private MongoTemplate mongoTemplate;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private JwtEncoder jwtEncoder;
  @Autowired private JwtConfig jwtConfig;
  @Autowired protected ObjectMapper objectMapper;

  protected MockMvc mockMvc;
  protected ApiTestFixtures fixtures;

  @BeforeEach
  protected void initIntegrationBase() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(context)
            .apply(SecurityMockMvcConfigurers.springSecurity())
            .build();
    fixtures = new ApiTestFixtures(mongoTemplate, passwordEncoder, jwtEncoder, jwtConfig);
  }

  @AfterEach
  protected void cleanIntegrationBase() {
    fixtures.cleanUp();
  }

  /** Truy cập {@link MongoTemplate} để đối chiếu trạng thái CSDL sau một lời gọi API. */
  protected MongoTemplate mongo() {
    return mongoTemplate;
  }

  // ---------------------------------------------------------------- dựng request

  protected MockHttpServletRequestBuilder getAs(String bearer, String url, Object... vars) {
    return withAuth(get(url, vars), bearer);
  }

  protected MockHttpServletRequestBuilder deleteAs(String bearer, String url, Object... vars) {
    return withAuth(delete(url, vars), bearer);
  }

  protected MockHttpServletRequestBuilder postAs(
      String bearer, String url, Object body, Object... vars) {
    return withJson(withAuth(post(url, vars), bearer), body);
  }

  protected MockHttpServletRequestBuilder putAs(
      String bearer, String url, Object body, Object... vars) {
    return withJson(withAuth(put(url, vars), bearer), body);
  }

  /** Gắn header {@code Authorization}; {@code bearer == null} nghĩa là không đăng nhập. */
  protected MockHttpServletRequestBuilder withAuth(
      MockHttpServletRequestBuilder builder, String bearer) {
    return bearer == null ? builder : builder.header(HttpHeaders.AUTHORIZATION, bearer);
  }

  /**
   * Gắn body JSON. {@code body} là {@code String} thì gửi nguyên văn (để thử JSON sai/trường thừa),
   * {@code null} thì không gửi body, còn lại được tuần tự hóa bằng ObjectMapper của ứng dụng.
   */
  protected MockHttpServletRequestBuilder withJson(
      MockHttpServletRequestBuilder builder, Object body) {
    builder.contentType(MediaType.APPLICATION_JSON);
    if (body instanceof String raw) {
      return builder.content(raw);
    }
    return body == null ? builder : builder.content(objectMapper.writeValueAsString(body));
  }

  /** Đọc một giá trị theo JsonPath từ phản hồi. */
  protected static <T> T read(MvcResult result, String jsonPath) throws Exception {
    return JsonPath.read(result.getResponse().getContentAsString(), jsonPath);
  }
}
