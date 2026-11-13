package com.hcmute.lms.lmsapi.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hcmute.lms.lmsapi.config.DemoDataSeeder.SeedResult;
import com.hcmute.lms.lmsapi.document.Course;
import com.hcmute.lms.lmsapi.document.Pi;
import com.hcmute.lms.lmsapi.document.Plo;
import com.hcmute.lms.lmsapi.document.Program;
import com.hcmute.lms.lmsapi.document.ProgramVersion;
import com.hcmute.lms.lmsapi.document.User;
import com.hcmute.lms.lmsapi.dto.LoginRequest;
import com.hcmute.lms.lmsapi.enums.PloCategory;
import com.hcmute.lms.lmsapi.enums.ProgramVersionStatus;
import com.hcmute.lms.lmsapi.enums.Role;
import com.hcmute.lms.lmsapi.enums.VerificationStatus;
import com.hcmute.lms.lmsapi.repository.CourseRepository;
import com.hcmute.lms.lmsapi.repository.PiRepository;
import com.hcmute.lms.lmsapi.repository.PloRepository;
import com.hcmute.lms.lmsapi.repository.ProgramRepository;
import com.hcmute.lms.lmsapi.repository.ProgramVersionRepository;
import com.hcmute.lms.lmsapi.repository.UserRepository;
import com.hcmute.lms.lmsapi.validator.PasswordPolicyValidator;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

/**
 * Issue #17: seed dữ liệu thử nghiệm bằng Spring profile {@code demo}.
 *
 * <p>Chạy trên context có profile {@code demo} với {@code app.demo.key} ngẫu nhiên riêng của test,
 * nên không đụng bản demo thật của người dùng trong cùng CSDL; mọi bản ghi theo key đó bị xóa sau
 * test. Số bản ghi kỳ vọng chép từ nội dung seeder được thiết kế: 1 CTĐT + 1 phiên bản + 2 PLO + 3
 * PI + 1 học phần + 3 dòng ma trận + 6 người dùng = 17.
 */
@SpringBootTest
@ActiveProfiles("demo")
@DisplayName("Issue #17: DemoDataSeeder (profile demo)")
class DemoDataSeederTest {

  private static final String LABEL = "[DỮ LIỆU THỬ NGHIỆM]";
  private static final String CONTEXT_KEY = "itest" + UUID.randomUUID().toString().substring(0, 8);
  private static final String PASSWORD = "Demo-" + UUID.randomUUID();
  private static final int EXPECTED_RECORDS = 12;

  /** Key của mọi seeder dựng tay trong test, để dọn ở cuối. */
  private static final java.util.Set<String> KEYS_TO_PURGE = new java.util.HashSet<>();

  @DynamicPropertySource
  static void demoProperties(DynamicPropertyRegistry registry) {
    registry.add("app.demo.key", () -> CONTEXT_KEY);
    registry.add("DEMO_USER_PASSWORD", () -> PASSWORD);
  }

  @Autowired private ApplicationContext applicationContext;
  @Autowired private WebApplicationContext webContext;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private MongoTemplate mongo;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private PasswordPolicyValidator passwordPolicyValidator;
  @Autowired private ProgramRepository programRepository;
  @Autowired private ProgramVersionRepository programVersionRepository;
  @Autowired private PloRepository ploRepository;
  @Autowired private PiRepository piRepository;
  @Autowired private CourseRepository courseRepository;
  @Autowired private UserRepository userRepository;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    KEYS_TO_PURGE.add(CONTEXT_KEY);
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webContext)
            .apply(SecurityMockMvcConfigurers.springSecurity())
            .build();
  }

  @AfterEach
  void purgeManualKeys() {
    KEYS_TO_PURGE.stream().filter(k -> !k.equals(CONTEXT_KEY)).forEach(this::purge);
    KEYS_TO_PURGE.removeIf(k -> !k.equals(CONTEXT_KEY));
  }

  @AfterAll
  static void purgeContextKey(@Autowired MongoTemplate mongo) {
    purge(mongo, CONTEXT_KEY);
  }

  private DemoDataSeeder seeder(String key, String password) {
    DemoDataSeeder created =
        new DemoDataSeeder(
            programRepository,
            programVersionRepository,
            ploRepository,
            piRepository,
            courseRepository,
            userRepository,
            passwordEncoder,
            passwordPolicyValidator);
    created.configure(password, key);
    KEYS_TO_PURGE.add(key);
    return created;
  }

  private static String freshKey() {
    return "itest" + UUID.randomUUID().toString().substring(0, 8);
  }

  private void purge(String key) {
    purge(mongo, key);
  }

  private static void purge(MongoTemplate mongo, String key) {
    // Chốt an toàn: chỉ xóa theo key ngẫu nhiên do chính test sinh ra, không bao giờ theo key tùy
    // ý.
    assertThat(key).matches("itest[0-9a-f]{8}");
    String upper = "^" + key.toUpperCase() + "-";
    List<ObjectId> programIds =
        mongo.find(Query.query(Criteria.where("code").regex(upper)), Program.class).stream()
            .map(p -> new ObjectId(p.getId()))
            .toList();
    List<ObjectId> versionIds =
        programIds.isEmpty()
            ? List.of()
            : mongo
                .find(Query.query(Criteria.where("programId").in(programIds)), ProgramVersion.class)
                .stream()
                .map(v -> new ObjectId(v.getId()))
                .toList();
    if (!versionIds.isEmpty()) {
      Query byVersion = Query.query(Criteria.where("programVersionId").in(versionIds));
      mongo.remove(byVersion, "course_pi_contributions");
      mongo.remove(byVersion, "pis");
      mongo.remove(byVersion, "plos");
      mongo.remove(Query.query(Criteria.where("programId").in(programIds)), "program_versions");
    }
    mongo.remove(Query.query(Criteria.where("code").regex(upper)), "programs");
    mongo.remove(Query.query(Criteria.where("code").regex(upper)), "courses");
    mongo.remove(Query.query(Criteria.where("username").regex("^" + key + "\\.")), "users");
  }

  private Program findProgram(DemoDataSeeder seeder) {
    return Objects.requireNonNull(
        mongo.findOne(Query.query(Criteria.where("code").is(seeder.programCode())), Program.class),
        "chương trình demo chưa được seed");
  }

  private ProgramVersion findVersion(Program program) {
    return Objects.requireNonNull(
        mongo.findOne(
            Query.query(Criteria.where("programId").is(new ObjectId(program.getId()))),
            ProgramVersion.class),
        "phiên bản CTĐT demo chưa được seed");
  }

  private long countRecords(DemoDataSeeder seeder) {
    Program program =
        mongo.findOne(Query.query(Criteria.where("code").is(seeder.programCode())), Program.class);
    if (program == null) {
      return 0;
    }
    ProgramVersion version = findVersion(program);
    ObjectId versionId = new ObjectId(version.getId());
    Query byVersion = Query.query(Criteria.where("programVersionId").is(versionId));
    return 1
        + mongo.count(
            Query.query(Criteria.where("programId").is(new ObjectId(program.getId()))),
            ProgramVersion.class)
        + mongo.count(byVersion, Plo.class)
        + mongo.count(byVersion, Pi.class)
        + mongo.count(Query.query(Criteria.where("code").is(seeder.courseCode())), Course.class)
        + mongo.count(
            Query.query(Criteria.where("username").regex("^" + seeder.username(""))), User.class);
  }

  // ------------------------------------------------------------------ profile

  @Test
  @DisplayName("Issue #17: profile demo bật -> seeder là bean và đã chạy lúc khởi động context")
  void seederRunsAtStartupWhenProfileDemoIsActive() {
    assertThat(applicationContext.getBeansOfType(DemoDataSeeder.class)).hasSize(1);
    DemoDataSeeder bean = applicationContext.getBean(DemoDataSeeder.class);
    assertThat(bean.programCode()).isEqualTo(CONTEXT_KEY.toUpperCase() + "-CTDT-THU-NGHIEM");
    assertThat(
            mongo.exists(Query.query(Criteria.where("code").is(bean.programCode())), Program.class))
        .isTrue();
    assertThat(userRepository.existsByUsername(bean.username("admin"))).isTrue();
  }

  // ------------------------------------------------------------------ nội dung và nhãn

  @Test
  @DisplayName(
      "Issue #17: tạo đủ 17 bản ghi, mọi bản ghi mang nhãn DỮ LIỆU THỬ NGHIỆM, không có dữ liệu thật")
  void seedsLabelledRecords() {
    DemoDataSeeder seeder = seeder(freshKey(), PASSWORD);

    SeedResult first = seeder.seed();

    assertThat(first.created()).isEqualTo(EXPECTED_RECORDS);
    assertThat(first.existing()).isZero();
    assertThat(first.usersSeeded()).isTrue();

    Program program =
        mongo.findOne(Query.query(Criteria.where("code").is(seeder.programCode())), Program.class);
    assertThat(program.getName()).startsWith(LABEL);
    assertThat(program.getLevel()).isNull();
    assertThat(program.getDegree()).isNull();

    ProgramVersion version = findVersion(program);
    assertThat(version.getVersion()).isEqualTo(seeder.versionCode());
    assertThat(version.getStatus()).isEqualTo(ProgramVersionStatus.DRAFT);
    assertThat(version.getTotalCredits()).as("số tín chỉ chưa được cung cấp").isNull();
    assertThat(version.getDurationYears()).isNull();
    assertThat(version.getApplicableCohorts()).as("Q01 chưa trả lời").isEmpty();
    assertThat(version.getVerification().getStatus())
        .isEqualTo(VerificationStatus.NEEDS_CLARIFICATION);
    assertThat(version.getVerification().getNote()).contains(LABEL);

    List<Plo> plos = ploRepository.findByProgramVersionIdOrderByDisplayOrderAsc(version.getId());
    // displayOrder cố ý ngược thứ tự mã: PLO2 hiển thị trước PLO1 (US-06/AC-4).
    assertThat(plos).extracting(Plo::getCode).containsExactly("PLO2", "PLO1");
    for (Plo plo : plos) {
      assertThat(plo.getContentSummary()).startsWith(LABEL);
      assertThat(plo.getContentOriginal()).as("không điền nguyên văn").isNull();
      assertThat(plo.getCapabilityLevelRaw()).as("không tự đặt mức năng lực").isNull();
      assertThat(plo.getCategory()).isEqualTo(PloCategory.UNSPECIFIED);
      assertThat(plo.getVerification().getStatus())
          .isEqualTo(VerificationStatus.NEEDS_CLARIFICATION);
    }

    List<Pi> pis = piRepository.findByProgramVersionIdOrderByDisplayOrderAsc(version.getId());
    assertThat(pis).extracting(Pi::getCode).containsExactly("PI1.1", "PI1.2", "PI2.1");
    Map<String, String> ploCodeById =
        plos.stream().collect(Collectors.toMap(Plo::getId, Plo::getCode));
    assertThat(pis)
        .extracting(pi -> ploCodeById.get(pi.getPloId()))
        .containsExactly("PLO1", "PLO1", "PLO2");
    for (Pi pi : pis) {
      assertThat(pi.getContentShort()).startsWith(LABEL);
      assertThat(pi.getContentOriginal()).isNull();
      assertThat(pi.getTargetPassRate()).as("không đặt ngưỡng mặc định").isNull();
      assertThat(pi.getCapabilityLevelRaw()).isNull();
      assertThat(pi.getVerification().getStatus())
          .isNotEqualTo(VerificationStatus.VERIFIED)
          .isEqualTo(VerificationStatus.NEEDS_CLARIFICATION);
    }

    Course course =
        mongo.findOne(Query.query(Criteria.where("code").is(seeder.courseCode())), Course.class);
    assertThat(course.getName()).startsWith(LABEL);
    assertThat(course.getCredits()).as("số tín chỉ thật do Khoa cung cấp").isNull();
  }

  @Test
  @DisplayName(
      "Issue #17: mỗi vai trò dùng trong cam kết có đúng một người dùng demo, nhãn trong họ tên")
  void seedsOneLabelledUserPerCommittedRole() {
    DemoDataSeeder seeder = seeder(freshKey(), PASSWORD);
    seeder.seed();

    Map<String, Role> expected =
        Map.of(
            "admin", Role.ADMIN,
            "manager", Role.PROGRAM_MANAGER,
            "lecturer", Role.LECTURER,
            "qa", Role.QA);
    expected.forEach(
        (suffix, role) -> {
          User user = userRepository.findByUsername(seeder.username(suffix)).orElseThrow();
          assertThat(user.getFullName()).startsWith(LABEL);
          assertThat(user.getEmail()).endsWith("@demo.invalid");
          assertThat(user.getRoleAssignments()).hasSize(1);
          assertThat(user.getRoleAssignments().get(0).getRole()).isEqualTo(role);
        });
    assertThat(
            mongo.count(
                Query.query(Criteria.where("username").regex("^" + seeder.username(""))),
                User.class))
        .isEqualTo(expected.size());
  }

  @Test
  @DisplayName("Issue #17: mật khẩu chỉ lưu dạng BCrypt của DEMO_USER_PASSWORD, không lưu bản rõ")
  void passwordIsHashedFromEnvironmentValue() {
    DemoDataSeeder seeder = seeder(freshKey(), PASSWORD);
    seeder.seed();

    User admin = userRepository.findByUsername(seeder.username("admin")).orElseThrow();
    assertThat(admin.getPasswordHash()).isNotEqualTo(PASSWORD).startsWith("$2");
    assertThat(passwordEncoder.matches(PASSWORD, admin.getPasswordHash())).isTrue();
    assertThat(passwordEncoder.matches(PASSWORD + "x", admin.getPasswordHash())).isFalse();
  }

  // ------------------------------------------------------------------ idempotent

  @Test
  @DisplayName("Issue #17: chạy lại không nhân đôi bản ghi và không ghi đè nội dung đã chỉnh")
  void isIdempotentAndDoesNotOverwrite() {
    DemoDataSeeder seeder = seeder(freshKey(), PASSWORD);
    seeder.seed();
    long before = countRecords(seeder);
    assertThat(before).isEqualTo(EXPECTED_RECORDS);

    ProgramVersion version =
        programVersionRepository
            .findByProgramId(
                findProgram(seeder).getId(), org.springframework.data.domain.Pageable.unpaged())
            .getContent()
            .get(0);
    Plo plo = ploRepository.findByProgramVersionIdOrderByDisplayOrderAsc(version.getId()).get(0);
    plo.setContentSummary("Người dùng đã chỉnh nội dung này");
    ploRepository.save(plo);

    SeedResult again = seeder.seed();

    assertThat(again.created()).isZero();
    assertThat(again.existing()).isEqualTo(EXPECTED_RECORDS);
    assertThat(countRecords(seeder)).isEqualTo(before);
    assertThat(ploRepository.findById(plo.getId()).orElseThrow().getContentSummary())
        .isEqualTo("Người dùng đã chỉnh nội dung này");
  }

  // ------------------------------------------------------------------ mật khẩu thiếu / sai

  @Test
  @DisplayName(
      "Issue #17: thiếu DEMO_USER_PASSWORD -> không tạo người dùng nào (không có mật khẩu mặc định), vẫn seed CTĐT")
  void missingPasswordSkipsUsers() {
    DemoDataSeeder seeder = seeder(freshKey(), "");

    SeedResult result = seeder.seed();

    assertThat(result.usersSeeded()).isFalse();
    assertThat(result.created()).isEqualTo(EXPECTED_RECORDS - 4);
    assertThat(
            mongo.count(
                Query.query(Criteria.where("username").regex("^" + seeder.username(""))),
                User.class))
        .isZero();
  }

  @Test
  @DisplayName(
      "Issue #17: mật khẩu vi phạm chính sách 8-72 byte -> bỏ qua người dùng, không băm mật khẩu yếu")
  void invalidPasswordSkipsUsers() {
    for (String bad : new String[] {"short7!", "a".repeat(73)}) {
      DemoDataSeeder seeder = seeder(freshKey(), bad);
      assertThat(seeder.seed().usersSeeded()).as("mật khẩu '%d' byte", bad.length()).isFalse();
      assertThat(userRepository.existsByUsername(seeder.username("admin"))).isFalse();
    }
  }

  @Test
  @DisplayName("Issue #17: app.demo.key sai định dạng -> từ chối khởi tạo seeder")
  void invalidKeyIsRejected() {
    for (String bad : new String[] {"", "A", "Demo", "demo key", "x".repeat(31), "demo.admin"}) {
      assertThatThrownBy(() -> seeder(bad, PASSWORD)).isInstanceOf(IllegalStateException.class);
    }
  }

  // ------------------------------------------------------------------ dùng được qua API

  @Test
  @DisplayName(
      "Issue #17: người dùng seed đăng nhập được; QA và manager xem được PLO demo; QA không ghi được")
  void seededUsersWorkEndToEnd() throws Exception {
    DemoDataSeeder seeder = applicationContext.getBean(DemoDataSeeder.class);
    seeder.seed();
    Program program = findProgram(seeder);
    ProgramVersion version = findVersion(program);

    String qa = login(seeder.username("qa"));
    String manager = login(seeder.username("manager"));

    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/v1/program-versions/{v}/plos", version.getId())
                .header(HttpHeaders.AUTHORIZATION, qa))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/v1/program-versions/{v}/plos", version.getId())
                .header(HttpHeaders.AUTHORIZATION, manager))
        .andExpect(status().isOk());
  }

  private String login(String username) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new LoginRequest(username, PASSWORD))))
            .andExpect(status().isOk())
            .andReturn();
    String token = JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    return "Bearer " + token;
  }
}
