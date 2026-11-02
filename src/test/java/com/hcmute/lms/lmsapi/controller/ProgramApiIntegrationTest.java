package com.hcmute.lms.lmsapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hcmute.lms.lmsapi.document.Program;
import com.hcmute.lms.lmsapi.dto.ProgramRequest;
import com.hcmute.lms.lmsapi.enums.Role;
import com.hcmute.lms.lmsapi.testsupport.ApiTestFixtures;
import com.hcmute.lms.lmsapi.testsupport.IntegrationTestBase;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MvcResult;

/** F02 / US-05: chương trình đào tạo ({@code programs}) qua HTTP. */
@DisplayName("F02 / US-05: API chương trình đào tạo")
class ProgramApiIntegrationTest extends IntegrationTestBase {

  private static final String PROGRAMS = "/api/v1/programs";
  private static final String PROGRAM_BY_ID = "/api/v1/programs/{id}";

  private String manager;

  @BeforeEach
  void setUp() {
    manager = fixtures.bearerFor(Role.PROGRAM_MANAGER, null);
  }

  private ProgramRequest request(String code) {
    return ProgramRequest.builder().code(code).name(ApiTestFixtures.LABEL + " CTĐT").build();
  }

  @Test
  @DisplayName("US-05: tạo CTĐT thành công, level/degree để trống được lưu null, không tự điền")
  void createProgram_blankOptionalFieldsBecomeNull() throws Exception {
    ProgramRequest body = request(fixtures.code("PRG"));
    body.setLevel("   ");
    body.setDegree("");

    MvcResult result =
        mockMvc
            .perform(postAs(manager, PROGRAMS, body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value(body.getCode()))
            .andExpect(jsonPath("$.level").value(nullValue()))
            .andExpect(jsonPath("$.degree").value(nullValue()))
            .andReturn();

    Program stored = mongo().findById(read(result, "$.id").toString(), Program.class);
    assertThat(stored.getLevel()).isNull();
    assertThat(stored.getDegree()).isNull();
  }

  @Test
  @DisplayName("US-05 (biên): mã được cắt khoảng trắng; mã trùng sau khi cắt -> 409 CONFLICT")
  void createProgram_codeIsTrimmedAndDuplicateIsConflict() throws Exception {
    String code = fixtures.code("PRG");
    mockMvc
        .perform(postAs(manager, PROGRAMS, request("  " + code + "  ")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.code").value(code));

    mockMvc
        .perform(postAs(manager, PROGRAMS, request(code)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"));
  }

  @Test
  @DisplayName("US-05 (âm): trường thừa (id, extra) bị bỏ qua, id luôn do hệ thống cấp")
  void createProgram_extraFieldsAreIgnored() throws Exception {
    String forgedId = ApiTestFixtures.missingId();
    String code = fixtures.code("PRG");
    String body =
        "{\"id\":\""
            + forgedId
            + "\",\"code\":\""
            + code
            + "\",\"name\":\"x\",\"extra\":{\"admin\":true},\"unknownField\":1}";
    mockMvc
        .perform(postAs(manager, PROGRAMS, body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(not(forgedId)));
  }

  @Test
  @DisplayName("US-05 (biên): mã CTĐT đúng 64 ký tự hợp lệ")
  void createProgram_codeAtMaxLength() throws Exception {
    int length = 64;
    String code = fixtures.code("L");
    String padded = code + "X".repeat(length - code.length());
    assertThat(padded).hasSize(length);
    mockMvc.perform(postAs(manager, PROGRAMS, request(padded))).andExpect(status().isCreated());
  }

  @Test
  @DisplayName(
      "US-05 (biên): mã 65 ký tự, tên 256 ký tự, level 101 ký tự -> 400 với đúng trường lỗi")
  void createProgram_lengthOverflow() throws Exception {
    ProgramRequest longCode = request("C".repeat(65));
    mockMvc
        .perform(postAs(manager, PROGRAMS, longCode))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("code")));

    ProgramRequest longName = request(fixtures.code("PRG"));
    longName.setName("N".repeat(256));
    mockMvc
        .perform(postAs(manager, PROGRAMS, longName))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("name")));

    ProgramRequest longLevel = request(fixtures.code("PRG"));
    longLevel.setLevel("L".repeat(101));
    mockMvc
        .perform(postAs(manager, PROGRAMS, longLevel))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("level")));
  }

  @Test
  @DisplayName(
      "US-40/AC-1: body rỗng, {} , JSON hỏng; mã/tên chỉ gồm khoảng trắng -> 400 VALIDATION_ERROR")
  void createProgram_invalidBodies() throws Exception {
    mockMvc
        .perform(postAs(manager, PROGRAMS, null))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    mockMvc.perform(postAs(manager, PROGRAMS, "{")).andExpect(status().isBadRequest());
    mockMvc
        .perform(postAs(manager, PROGRAMS, "{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("code")))
        .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("name")));
    ProgramRequest blank = ProgramRequest.builder().code("   ").name("\t").build();
    mockMvc
        .perform(postAs(manager, PROGRAMS, blank))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("code")))
        .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("name")));
  }

  // ------------------------------------------------------------------ đọc / sửa

  @Test
  @DisplayName("US-05: sửa CTĐT 200; đổi sang mã đã có -> 409; giữ nguyên mã -> 200; id lạ -> 404")
  void updateProgram() throws Exception {
    Program first = fixtures.program();
    Program second = fixtures.program();
    ProgramRequest rename = request(first.getCode());

    mockMvc
        .perform(putAs(manager, PROGRAM_BY_ID, rename, second.getId()))
        .andExpect(status().isConflict());

    ProgramRequest keepCode = request(second.getCode());
    keepCode.setLevel("Đại học");
    mockMvc
        .perform(putAs(manager, PROGRAM_BY_ID, keepCode, second.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.level").value("Đại học"));
    assertThat(mongo().findById(second.getId(), Program.class).getLevel()).isEqualTo("Đại học");

    mockMvc
        .perform(putAs(manager, PROGRAM_BY_ID, keepCode, ApiTestFixtures.missingId()))
        .andExpect(status().isNotFound());
  }

  @ParameterizedTest(name = "{0} -> 400")
  @ValueSource(strings = {"abc", "12345", "zzzzzzzzzzzzzzzzzzzzzzzz", "671dcba5f123456789abc7777"})
  @DisplayName("US-40 (biên): ObjectId sai định dạng ở path -> 400 VALIDATION_ERROR, không 500")
  void malformedId(String id) throws Exception {
    mockMvc
        .perform(getAs(manager, PROGRAM_BY_ID, id))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    mockMvc
        .perform(putAs(manager, PROGRAM_BY_ID, request("X"), id))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("US-40: GET CTĐT với ObjectId hợp lệ nhưng không tồn tại -> 404 NOT_FOUND")
  void unknownId() throws Exception {
    mockMvc
        .perform(getAs(manager, PROGRAM_BY_ID, ApiTestFixtures.missingId()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  // ------------------------------------------------------------------ phân trang

  @ParameterizedTest(name = "{0} -> 400")
  @ValueSource(strings = {"page=-1", "size=0", "size=101", "size=-1", "page=x", "size=x"})
  @DisplayName("US-02/AC-6 (áp dụng cho F02): page/size ngoài biên -> 400 VALIDATION_ERROR")
  void invalidPagination(String query) throws Exception {
    mockMvc
        .perform(getAs(manager, PROGRAMS + "?" + query))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  @Test
  @DisplayName(
      "US-02/AC-6 (áp dụng cho F02): size = 1 trả đúng 1 phần tử; totalPages = ceil(total / size)")
  void paginationEnvelopeIsConsistent() throws Exception {
    fixtures.program();
    fixtures.program();
    fixtures.program();

    MvcResult single =
        mockMvc
            .perform(getAs(manager, PROGRAMS + "?page=0&size=1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(1))
            .andReturn();
    // Tổng số bản ghi trong DB không biết trước (có dữ liệu khởi tạo), nên so sánh nội bộ:
    // totalPages phải bằng totalElements khi size = 1, và trang cuối là trang totalPages - 1.
    int total = ((Number) read(single, "$.totalElements")).intValue();
    assertThat(total).isGreaterThanOrEqualTo(3);
    assertThat(read(single, "$.totalPages").toString()).isEqualTo(String.valueOf(total));
    assertThat(read(single, "$.last").toString()).isEqualTo("false");

    mockMvc
        .perform(getAs(manager, PROGRAMS + "?page=" + (total - 1) + "&size=1"))
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.last").value(true));
    mockMvc
        .perform(getAs(manager, PROGRAMS + "?page=" + total + "&size=1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(0))
        .andExpect(jsonPath("$.last").value(true));
  }

  @Test
  @DisplayName("US-05: danh sách CTĐT sắp theo code tăng dần")
  void listIsSortedByCode() throws Exception {
    MvcResult page =
        mockMvc
            .perform(getAs(manager, PROGRAMS + "?size=100"))
            .andExpect(status().isOk())
            .andReturn();
    List<String> codes = read(page, "$.content[*].code");
    List<String> expected = new ArrayList<>(codes);
    expected.sort(String::compareTo);
    assertThat(codes).isEqualTo(expected);
  }

  // ------------------------------------------------------------------ quyền

  @Test
  @DisplayName("US-03/AC-2: không đăng nhập -> 401 trên mọi thao tác programs")
  void anonymousIsUnauthorized() throws Exception {
    Program program = fixtures.program();
    mockMvc.perform(getAs(null, PROGRAMS)).andExpect(status().isUnauthorized());
    mockMvc
        .perform(getAs(null, PROGRAM_BY_ID, program.getId()))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(postAs(null, PROGRAMS, request("X"))).andExpect(status().isUnauthorized());
    mockMvc
        .perform(putAs(null, PROGRAM_BY_ID, request("X"), program.getId()))
        .andExpect(status().isUnauthorized());
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(Role.class)
  @DisplayName("F02 mục 6: mọi vai trò đọc được CTĐT; chỉ PROGRAM_MANAGER ghi, vai trò khác -> 403")
  void readAllWriteOnlyProgramManager(Role role) throws Exception {
    String bearer = fixtures.bearerFor(role, null);
    Program program = fixtures.program();

    mockMvc.perform(getAs(bearer, PROGRAMS)).andExpect(status().isOk());
    mockMvc.perform(getAs(bearer, PROGRAM_BY_ID, program.getId())).andExpect(status().isOk());

    boolean mayWrite = role == Role.PROGRAM_MANAGER;
    var create = mockMvc.perform(postAs(bearer, PROGRAMS, request(fixtures.code("PRG"))));
    var update =
        mockMvc.perform(putAs(bearer, PROGRAM_BY_ID, request(program.getCode()), program.getId()));
    if (mayWrite) {
      create.andExpect(status().isCreated());
      update.andExpect(status().isOk());
    } else {
      create.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
      update.andExpect(status().isForbidden());
    }
  }
}
