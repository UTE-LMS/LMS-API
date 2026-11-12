package com.hcmute.lms.lmsapi.config;

import com.hcmute.lms.lmsapi.document.Course;
import com.hcmute.lms.lmsapi.document.CoursePiContribution;
import com.hcmute.lms.lmsapi.document.Pi;
import com.hcmute.lms.lmsapi.document.Plo;
import com.hcmute.lms.lmsapi.document.Program;
import com.hcmute.lms.lmsapi.document.ProgramVersion;
import com.hcmute.lms.lmsapi.document.RoleAssignment;
import com.hcmute.lms.lmsapi.document.User;
import com.hcmute.lms.lmsapi.document.UserScope;
import com.hcmute.lms.lmsapi.document.Verification;
import com.hcmute.lms.lmsapi.enums.PiContributionType;
import com.hcmute.lms.lmsapi.enums.PloCategory;
import com.hcmute.lms.lmsapi.enums.ProgramVersionStatus;
import com.hcmute.lms.lmsapi.enums.Role;
import com.hcmute.lms.lmsapi.enums.UserStatus;
import com.hcmute.lms.lmsapi.enums.VerificationStatus;
import com.hcmute.lms.lmsapi.repository.CoursePiContributionRepository;
import com.hcmute.lms.lmsapi.repository.CourseRepository;
import com.hcmute.lms.lmsapi.repository.PiRepository;
import com.hcmute.lms.lmsapi.repository.PloRepository;
import com.hcmute.lms.lmsapi.repository.ProgramRepository;
import com.hcmute.lms.lmsapi.repository.ProgramVersionRepository;
import com.hcmute.lms.lmsapi.repository.UserRepository;
import com.hcmute.lms.lmsapi.validator.PasswordPolicyValidator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeder dữ liệu thử nghiệm cho demo (issue #17), chỉ chạy khi Spring profile {@code demo} bật.
 *
 * <p>Chỉ tạo cho các collection đã có mã: {@code users} (mỗi vai trò dùng trong cam kết một người),
 * {@code programs}, {@code program_versions}, {@code plos}, {@code pis}, {@code courses} và {@code
 * course_pi_contributions}. Chưa có học phần/CLO, bài đánh giá, lớp, điểm vì chưa có mã (F03-F05b).
 *
 * <p>Nguyên tắc (CLAUDE.md quy tắc 2, plan README mục 5):
 *
 * <ul>
 *   <li>Mọi bản ghi mang nhãn {@link #LABEL}; nội dung PLO/PI là chữ giả, {@code contentOriginal}
 *       để trống, xác nhận mặc định {@code NEEDS_CLARIFICATION}, không đặt {@code
 *       capabilityLevelRaw}, {@code targetPassRate}, tín chỉ, khóa áp dụng hay ngưỡng/trọng số.
 *   <li>Mật khẩu đọc từ biến môi trường {@code DEMO_USER_PASSWORD}, băm BCrypt; không có mật khẩu
 *       hoặc mật khẩu vi phạm chính sách thì bỏ qua phần người dùng (không dùng mật khẩu mặc định).
 *   <li>Idempotent: tìm theo khóa tự nhiên (username, mã, cặp khóa duy nhất), chưa có mới tạo, đã
 *       có thì giữ nguyên, không ghi đè dữ liệu người dùng đã chỉnh.
 *   <li>Mọi định danh có tiền tố {@code app.demo.key} (mặc định {@code demo}) để test có thể dùng
 *       tiền tố riêng mà không đụng dữ liệu demo thật.
 * </ul>
 */
@Slf4j
@Component
@Profile("demo")
@Order(DemoDataSeeder.ORDER)
public final class DemoDataSeeder implements ApplicationRunner {

  /** Chạy sau {@code InitialAdminSeeder} (Order 1) để không làm nó thấy users đã có dữ liệu. */
  public static final int ORDER = 2;

  /** Nhãn bắt buộc cho dữ liệu giả. */
  public static final String LABEL = "[DỮ LIỆU THỬ NGHIỆM]";

  private static final String FAKE_NOTE = ", không phải nội dung CTĐT thật";

  private static final Pattern KEY_PATTERN = Pattern.compile("[a-z0-9]{2,30}");

  private final ProgramRepository programRepository;
  private final ProgramVersionRepository programVersionRepository;
  private final PloRepository ploRepository;
  private final PiRepository piRepository;
  private final CourseRepository courseRepository;
  private final CoursePiContributionRepository contributionRepository;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final PasswordPolicyValidator passwordPolicyValidator;
  private final String password;
  private final String key;

  /** Tạo seeder; {@code password} và {@code key} lấy từ biến môi trường / cấu hình. */
  public DemoDataSeeder(
      ProgramRepository programRepository,
      ProgramVersionRepository programVersionRepository,
      PloRepository ploRepository,
      PiRepository piRepository,
      CourseRepository courseRepository,
      CoursePiContributionRepository contributionRepository,
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      PasswordPolicyValidator passwordPolicyValidator,
      @Value("${DEMO_USER_PASSWORD:}") String password,
      @Value("${app.demo.key:demo}") String key) {
    if (key == null || !KEY_PATTERN.matcher(key).matches()) {
      throw new IllegalStateException("app.demo.key phải khớp [a-z0-9]{2,30}");
    }
    this.programRepository = programRepository;
    this.programVersionRepository = programVersionRepository;
    this.ploRepository = ploRepository;
    this.piRepository = piRepository;
    this.courseRepository = courseRepository;
    this.contributionRepository = contributionRepository;
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.passwordPolicyValidator = passwordPolicyValidator;
    this.password = password == null ? "" : password;
    this.key = key;
  }

  /** Kết quả một lần seed: số bản ghi mới tạo và số bản ghi đã có sẵn (giữ nguyên). */
  public record SeedResult(int created, int existing, boolean usersSeeded) {}

  /** Mã định danh của chương trình mẫu. */
  public String programCode() {
    return key.toUpperCase() + "-CTDT-THU-NGHIEM";
  }

  /** Mã phiên bản của CTĐT mẫu. */
  public String versionCode() {
    return key.toUpperCase() + "-PHIEN-BAN-THU-NGHIEM";
  }

  /** Mã học phần mẫu (chỉ để minh họa ma trận I/R/M). */
  public String courseCode() {
    return key.toUpperCase() + "-HP-THU-NGHIEM";
  }

  /** Username của người dùng demo theo vai trò, ví dụ {@code demo.qa}. */
  public String username(String suffix) {
    return key + "." + suffix;
  }

  @Override
  public void run(ApplicationArguments args) {
    SeedResult result = seed();
    log.info(
        "Seed dữ liệu thử nghiệm xong: tạo mới {}, đã có {}, người dùng demo {}",
        result.created(),
        result.existing(),
        result.usersSeeded() ? "đã xử lý" : "bỏ qua");
  }

  /** Tạo các bản ghi còn thiếu; chạy lại nhiều lần cho cùng kết quả. */
  public SeedResult seed() {
    Counter counter = new Counter();
    Program program = ensureProgram(counter);
    ProgramVersion version = ensureVersion(program, counter);
    Map<String, Plo> plos = ensurePlos(version, counter);
    Map<String, Pi> pis = ensurePis(version, plos, counter);
    Course course = ensureCourse(counter);
    ensureContributions(version, course, pis, counter);
    boolean usersSeeded = ensureUsers(program, course, counter);
    return new SeedResult(counter.created, counter.existing, usersSeeded);
  }

  // ---------------------------------------------------------------- CTĐT, PLO, PI, ma trận

  private Program ensureProgram(Counter counter) {
    Optional<Program> found =
        programRepository.findOne(Example.of(Program.builder().code(programCode()).build()));
    if (found.isPresent()) {
      counter.existing++;
      return found.get();
    }
    counter.created++;
    Map<String, Object> extra = new HashMap<>();
    extra.put("label", LABEL);
    return saveOrReload(
        () ->
            programRepository.save(
                Program.builder()
                    .code(programCode())
                    .name(LABEL + " Chương trình đào tạo mẫu")
                    .extra(extra)
                    .build()),
        () -> programRepository.findOne(Example.of(Program.builder().code(programCode()).build())));
  }

  private ProgramVersion ensureVersion(Program program, Counter counter) {
    Optional<ProgramVersion> found = findVersion(program);
    if (found.isPresent()) {
      counter.existing++;
      return found.get();
    }
    counter.created++;
    return saveOrReload(
        () ->
            programVersionRepository.save(
                ProgramVersion.builder()
                    .programId(program.getId())
                    .version(versionCode())
                    .status(ProgramVersionStatus.DRAFT)
                    .applicableCohorts(new ArrayList<>())
                    .verification(unverified("phiên bản mẫu, chưa đối chiếu CTĐT gốc"))
                    .build()),
        () -> findVersion(program));
  }

  private Optional<ProgramVersion> findVersion(Program program) {
    return programVersionRepository.findByProgramId(program.getId(), Pageable.unpaged()).stream()
        .filter(v -> versionCode().equals(v.getVersion()))
        .findFirst();
  }

  private Map<String, Plo> ensurePlos(ProgramVersion version, Counter counter) {
    Map<String, Plo> byCode = new HashMap<>();
    ploRepository
        .findByProgramVersionIdOrderByDisplayOrderAsc(version.getId())
        .forEach(p -> byCode.put(p.getCode(), p));
    // displayOrder cố ý ngược thứ tự mã (PLO2 hiển thị trước PLO1) để minh họa US-06/AC-4.
    ensurePlo(version, byCode, "PLO1", 2, counter);
    ensurePlo(version, byCode, "PLO2", 1, counter);
    return byCode;
  }

  private void ensurePlo(
      ProgramVersion version, Map<String, Plo> byCode, String code, int order, Counter counter) {
    if (byCode.containsKey(code)) {
      counter.existing++;
      return;
    }
    counter.created++;
    Plo plo =
        saveOrReload(
            () ->
                ploRepository.save(
                    Plo.builder()
                        .programVersionId(version.getId())
                        .code(code)
                        .displayOrder(order)
                        .category(PloCategory.UNSPECIFIED)
                        .contentSummary(LABEL + " Nội dung giả của " + code + FAKE_NOTE)
                        .passThreshold(0.5)
                        .verification(unverified("PLO mẫu, nội dung giả"))
                        .build()),
            () ->
                ploRepository.findByProgramVersionIdOrderByDisplayOrderAsc(version.getId()).stream()
                    .filter(p -> code.equals(p.getCode()))
                    .findFirst());
    byCode.put(code, plo);
  }

  private Map<String, Pi> ensurePis(
      ProgramVersion version, Map<String, Plo> plos, Counter counter) {
    Map<String, Pi> byCode = new HashMap<>();
    piRepository
        .findByProgramVersionIdOrderByDisplayOrderAsc(version.getId())
        .forEach(p -> byCode.put(p.getCode(), p));
    ensurePi(version, plos.get("PLO1"), byCode, "PI1.1", 1, counter);
    ensurePi(version, plos.get("PLO1"), byCode, "PI1.2", 2, counter);
    ensurePi(version, plos.get("PLO2"), byCode, "PI2.1", 3, counter);
    return byCode;
  }

  private void ensurePi(
      ProgramVersion version,
      Plo plo,
      Map<String, Pi> byCode,
      String code,
      int order,
      Counter counter) {
    if (byCode.containsKey(code)) {
      counter.existing++;
      return;
    }
    counter.created++;
    Pi pi =
        saveOrReload(
            () ->
                piRepository.save(
                    Pi.builder()
                        .programVersionId(version.getId())
                        .ploId(plo.getId())
                        .code(code)
                        .displayOrder(order)
                        .contentShort(LABEL + " Nội dung giả của " + code + FAKE_NOTE)
                        .weight(0.5)
                        .passThreshold(0.5)
                        .verification(unverified("PI mẫu, nội dung giả"))
                        .build()),
            () ->
                piRepository.findByProgramVersionIdOrderByDisplayOrderAsc(version.getId()).stream()
                    .filter(p -> code.equals(p.getCode()))
                    .findFirst());
    byCode.put(code, pi);
  }

  private Course ensureCourse(Counter counter) {
    Optional<Course> found =
        courseRepository.findOne(Example.of(Course.builder().code(courseCode()).build()));
    if (found.isPresent()) {
      counter.existing++;
      return found.get();
    }
    counter.created++;
    // credits để trống: số tín chỉ thật do Khoa cung cấp (F03).
    return saveOrReload(
        () ->
            courseRepository.save(
                Course.builder().code(courseCode()).name(LABEL + " Học phần mẫu").build()),
        () -> courseRepository.findOne(Example.of(Course.builder().code(courseCode()).build())));
  }

  private void ensureContributions(
      ProgramVersion version, Course course, Map<String, Pi> pis, Counter counter) {
    // Ma trận minh họa; I/R/M là vai trò sư phạm, không có hệ số (F02 mục 4.6).
    ensureContribution(version, course, pis.get("PI1.1"), PiContributionType.I, counter);
    ensureContribution(version, course, pis.get("PI1.2"), PiContributionType.R, counter);
    ensureContribution(version, course, pis.get("PI2.1"), PiContributionType.M, counter);
  }

  private void ensureContribution(
      ProgramVersion version, Course course, Pi pi, PiContributionType type, Counter counter) {
    if (contributionRepository.existsByProgramVersionIdAndCourseIdAndPiId(
        version.getId(), course.getId(), pi.getId())) {
      counter.existing++;
      return;
    }
    counter.created++;
    try {
      contributionRepository.save(
          CoursePiContribution.builder()
              .programVersionId(version.getId())
              .courseId(course.getId())
              .piId(pi.getId())
              .contribution(type)
              .verification(unverified("dòng ma trận mẫu"))
              .build());
    } catch (DuplicateKeyException ex) {
      log.debug("Dòng ma trận demo đã được tạo song song, bỏ qua: {}", ex.getMessage());
    }
  }

  // ---------------------------------------------------------------- người dùng

  private boolean ensureUsers(Program program, Course course, Counter counter) {
    if (password.isEmpty()) {
      log.warn(
          "Profile demo bật nhưng DEMO_USER_PASSWORD chưa đặt: bỏ qua tạo người dùng demo "
              + "(không dùng mật khẩu mặc định).");
      return false;
    }
    if (!passwordPolicyValidator.isValid(password)) {
      log.error(
          "DEMO_USER_PASSWORD không thỏa chính sách mật khẩu ({} đến {} byte UTF-8): bỏ qua tạo "
              + "người dùng demo.",
          PasswordPolicyValidator.MIN_BYTES,
          PasswordPolicyValidator.MAX_BYTES);
      return false;
    }
    String hash = passwordEncoder.encode(password);
    UserScope programScope = UserScope.builder().programIds(List.of(program.getId())).build();
    UserScope courseScope = UserScope.builder().courseIds(List.of(course.getId())).build();

    ensureUser("admin", "Quản trị viên", Role.ADMIN, null, hash, counter);
    ensureUser("manager", "Quản lý CTĐT", Role.PROGRAM_MANAGER, programScope, hash, counter);
    ensureUser("head", "Trưởng bộ môn", Role.DEPARTMENT_HEAD, courseScope, hash, counter);
    ensureUser("qa", "Cán bộ đảm bảo chất lượng", Role.QA_OFFICER, programScope, hash, counter);
    ensureUser("lecturer", "Giảng viên", Role.LECTURER, courseScope, hash, counter);
    ensureUser("viewer", "Người xem", Role.VIEWER, null, hash, counter);
    return true;
  }

  private void ensureUser(
      String suffix,
      String title,
      Role role,
      UserScope scope,
      String passwordHash,
      Counter counter) {
    String username = username(suffix);
    if (userRepository.existsByUsername(username)) {
      counter.existing++;
      return;
    }
    counter.created++;
    Instant now = Instant.now();
    try {
      userRepository.save(
          User.builder()
              .username(username)
              .email(username + "@demo.invalid")
              .passwordHash(passwordHash)
              .fullName(LABEL + " " + title)
              .status(UserStatus.ACTIVE)
              .tokenVersion(0L)
              .roleAssignments(List.of(RoleAssignment.builder().role(role).scope(scope).build()))
              .createdAt(now)
              .updatedAt(now)
              .build());
    } catch (DuplicateKeyException ex) {
      log.debug("Người dùng demo đã được tạo song song, bỏ qua: {}", username);
    }
  }

  // ---------------------------------------------------------------- tiện ích

  private static Verification unverified(String what) {
    return Verification.builder()
        .status(VerificationStatus.NEEDS_CLARIFICATION)
        .note(LABEL + " " + what + "; chưa được ai xác nhận.")
        .build();
  }

  /** Lưu; nếu một tiến trình khác vừa tạo cùng khóa duy nhất thì đọc lại bản đó. */
  private static <T> T saveOrReload(
      java.util.function.Supplier<T> save, java.util.function.Supplier<Optional<T>> reload) {
    try {
      return save.get();
    } catch (DuplicateKeyException ex) {
      return reload
          .get()
          .orElseThrow(() -> new IllegalStateException("Không đọc lại được bản ghi demo", ex));
    }
  }

  private static final class Counter {
    private int created;
    private int existing;
  }
}
