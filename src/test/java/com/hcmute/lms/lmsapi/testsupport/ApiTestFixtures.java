package com.hcmute.lms.lmsapi.testsupport;

import com.hcmute.lms.lmsapi.config.JwtConfig;
import com.hcmute.lms.lmsapi.document.Course;
import com.hcmute.lms.lmsapi.document.CoursePiContribution;
import com.hcmute.lms.lmsapi.document.CourseSection;
import com.hcmute.lms.lmsapi.document.Enrollment;
import com.hcmute.lms.lmsapi.document.Pi;
import com.hcmute.lms.lmsapi.document.Plo;
import com.hcmute.lms.lmsapi.document.Program;
import com.hcmute.lms.lmsapi.document.ProgramCourse;
import com.hcmute.lms.lmsapi.document.ProgramVersion;
import com.hcmute.lms.lmsapi.document.RoleAssignment;
import com.hcmute.lms.lmsapi.document.Student;
import com.hcmute.lms.lmsapi.document.Syllabus;
import com.hcmute.lms.lmsapi.document.Term;
import com.hcmute.lms.lmsapi.document.User;
import com.hcmute.lms.lmsapi.document.UserScope;
import com.hcmute.lms.lmsapi.document.Verification;
import com.hcmute.lms.lmsapi.enums.CourseCategory;
import com.hcmute.lms.lmsapi.enums.CourseType;
import com.hcmute.lms.lmsapi.enums.EnrollmentStatus;
import com.hcmute.lms.lmsapi.enums.PiContributionType;
import com.hcmute.lms.lmsapi.enums.ProgramVersionStatus;
import com.hcmute.lms.lmsapi.enums.Role;
import com.hcmute.lms.lmsapi.enums.SectionStatus;
import com.hcmute.lms.lmsapi.enums.SyllabusStatus;
import com.hcmute.lms.lmsapi.enums.TermStatus;
import com.hcmute.lms.lmsapi.enums.UserStatus;
import com.hcmute.lms.lmsapi.enums.VerificationStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

/**
 * Tạo và dọn dữ liệu thử nghiệm cho integration test (issue #52).
 *
 * <p>Mỗi instance có một {@code runId} ngẫu nhiên. Mọi bản ghi tạo qua lớp này (hoặc qua API với mã
 * sinh bởi {@link #code(String)} và {@link #username(String)}) mang tiền tố {@code itest} kèm
 * {@code runId}, nhờ đó {@link #cleanUp()} chỉ xóa đúng dữ liệu của chính test này và test không
 * phụ thuộc thứ tự chạy. Không đụng tới dữ liệu khởi tạo của {@code database_import.js}.
 *
 * <p>Token JWT được ký trực tiếp bằng {@link JwtEncoder} của ứng dụng, không đi qua {@code
 * /auth/login}, nên không sinh thêm dòng {@code audit_logs} ngoài ý muốn.
 */
@RequiredArgsConstructor
public final class ApiTestFixtures {

  /** Nhãn bắt buộc cho dữ liệu giả (CLAUDE.md quy tắc 2, plan README bất biến 11). */
  public static final String LABEL = "[DỮ LIỆU THỬ NGHIỆM]";

  private final MongoTemplate mongo;
  private final PasswordEncoder passwordEncoder;
  private final JwtEncoder jwtEncoder;
  private final JwtConfig jwtConfig;
  private final String runId = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
  private String passwordHash;
  private final AtomicInteger sequence = new AtomicInteger();
  private final List<String> extraUsernames = new ArrayList<>();

  /** Băm mật khẩu ngẫu nhiên đúng một lần cho mỗi bộ fixture; các user fixture dùng chung. */
  private synchronized String sharedPasswordHash() {
    if (passwordHash == null) {
      passwordHash = passwordEncoder.encode("Pw-" + UUID.randomUUID());
    }
    return passwordHash;
  }

  /** Mã chạy riêng của test này, dùng để dựng tên không đụng nhau. */
  public String runId() {
    return runId;
  }

  /** Sinh mã học phần/chương trình duy nhất theo test, ví dụ {@code ITEST-3F2A..-PRG-1}. */
  public String code(String kind) {
    return "ITEST-" + runId.toUpperCase() + "-" + kind + "-" + sequence.incrementAndGet();
  }

  /**
   * Đăng ký thêm một username (đã chuẩn hóa chữ thường) cần dọn sau test, dùng cho ca biên mà tên
   * không thể mang tiền tố, ví dụ username đúng 3 ký tự.
   */
  public String trackUsername(String username) {
    extraUsernames.add(username);
    return username;
  }

  /** Sinh username duy nhất theo test (chữ thường, hợp lệ với mẫu {@code [a-zA-Z0-9._-]+}). */
  public String username(String kind) {
    return "itest_" + runId + "_" + kind + "_" + sequence.incrementAndGet();
  }

  // ---------------------------------------------------------------- người dùng và token

  /** Tạo người dùng ACTIVE với đúng một phân công vai trò. */
  public User user(Role role, UserScope scope) {
    return user(List.of(RoleAssignment.builder().role(role).scope(scope).build()));
  }

  /** Tạo người dùng ACTIVE có mật khẩu biết trước, dùng cho test đăng nhập. */
  public User userWithPassword(Role role, UserScope scope, String rawPassword) {
    User user = user(role, scope);
    user.setPasswordHash(passwordEncoder.encode(rawPassword));
    return mongo.save(user);
  }

  /** Tạo người dùng ACTIVE với nhiều phân công vai trò (quyền là phép hợp). */
  public User user(List<RoleAssignment> assignments) {
    String username = username("user");
    Instant now = Instant.now();
    return mongo.save(
        User.builder()
            .username(username)
            .email(username + "@itest.invalid")
            .passwordHash(sharedPasswordHash())
            .fullName(LABEL + " " + username)
            .status(UserStatus.ACTIVE)
            .tokenVersion(0L)
            .roleAssignments(new ArrayList<>(assignments))
            .createdAt(now)
            .updatedAt(now)
            .build());
  }

  /** Token Bearer hợp lệ của người dùng, dạng {@code "Bearer <jwt>"}. */
  public String bearer(User user) {
    Instant now = Instant.now();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(jwtConfig.getIssuer())
            .subject(user.getId())
            .issuedAt(now)
            .expiresAt(now.plus(jwtConfig.getTtl()))
            .claim("tokenVersion", user.getTokenVersion())
            .build();
    String token =
        jwtEncoder
            .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();
    return "Bearer " + token;
  }

  /** Tạo người dùng rồi trả luôn token Bearer của họ. */
  public String bearerFor(Role role, UserScope scope) {
    return bearer(user(role, scope));
  }

  // ---------------------------------------------------------------- CTĐT (F02)

  /** Tạo chương trình đào tạo giả. */
  public Program program() {
    return mongo.save(
        Program.builder().code(code("PRG")).name(LABEL + " Chương trình thử nghiệm").build());
  }

  /** Tạo phiên bản CTĐT giả; xác nhận mặc định là chưa xác nhận. */
  public ProgramVersion programVersion(Program program, ProgramVersionStatus status) {
    return mongo.save(
        ProgramVersion.builder()
            .programId(program.getId())
            .version("V-" + runId + "-" + sequence.incrementAndGet())
            .status(status)
            .verification(unverified())
            .build());
  }

  /** Tạo PLO giả. {@code contentOriginal} để trống đúng quy tắc F02 mục 4.4. */
  public Plo plo(ProgramVersion version, String code, int displayOrder) {
    return mongo.save(
        Plo.builder()
            .programVersionId(version.getId())
            .code(code)
            .displayOrder(displayOrder)
            .contentSummary(LABEL + " PLO giả " + code)
            .verification(unverified())
            .build());
  }

  /** Tạo PI giả thuộc một PLO. */
  public Pi pi(ProgramVersion version, Plo plo, String code, int displayOrder) {
    return mongo.save(
        Pi.builder()
            .programVersionId(version.getId())
            .ploId(plo.getId())
            .code(code)
            .displayOrder(displayOrder)
            .contentShort(LABEL + " PI giả " + code)
            .verification(unverified())
            .build());
  }

  /** Tạo học phần giả. */
  public Course course() {
    return mongo.save(Course.builder().code(code("CRS")).name(LABEL + " Học phần giả").build());
  }

  /** Gán học phần vào phiên bản CTĐT (cơ sở suy phạm vi của đề cương/CLO, ADR 0009). */
  public ProgramCourse programCourse(ProgramVersion version, Course course) {
    return mongo.save(
        ProgramCourse.builder()
            .programVersionId(version.getId())
            .courseId(course.getId())
            .semester(1)
            .credits(3)
            .type(CourseType.COMPULSORY)
            .category(CourseCategory.MAJOR_FOUNDATION)
            .build());
  }

  /** Thêm một dòng ma trận I/R/M giả. */
  public CoursePiContribution contribution(
      ProgramVersion version, Course course, Pi pi, PiContributionType type) {
    return mongo.save(
        CoursePiContribution.builder()
            .programVersionId(version.getId())
            .courseId(course.getId())
            .piId(pi.getId())
            .contribution(type)
            .verification(unverified())
            .build());
  }

  /** Tạo học kỳ giả đang mở (OPEN), mã mang tiền tố của test này để được dọn tự động. */
  public Term term() {
    return term(TermStatus.OPEN);
  }

  /** Tạo học kỳ giả với trạng thái cho trước. */
  public Term term(TermStatus status) {
    return mongo.save(
        Term.builder().code(code("TRM")).academicYear("2025-2026").status(status).build());
  }

  /** Tạo đề cương giả của một học phần với trạng thái cho trước. */
  public Syllabus syllabus(Course course, SyllabusStatus status) {
    return mongo.save(
        Syllabus.builder()
            .courseId(course.getId())
            .version("V-" + runId + "-" + sequence.incrementAndGet())
            .status(status)
            .verification(unverified())
            .build());
  }

  /** Tạo lớp học phần giả với danh sách giảng viên phụ trách cho trước. */
  public CourseSection section(String courseId, List<String> lecturerIds) {
    return mongo.save(
        CourseSection.builder()
            .id(new ObjectId().toHexString())
            .code(code("SEC"))
            .courseId(courseId)
            .syllabusId(new ObjectId().toHexString())
            .termId(new ObjectId().toHexString())
            .lecturerIds(new ArrayList<>(lecturerIds))
            .enrolledCount(0)
            .status(SectionStatus.OPEN)
            .build());
  }

  /** Tạo sinh viên giả với mã thay thế mang tiền tố của test này (không phải mã sinh viên thật). */
  public Student student() {
    return mongo.save(Student.builder().studentRef(code("STU")).build());
  }

  /** Ghi danh một sinh viên vào lớp với trạng thái cho trước (không đụng tới sĩ số của lớp). */
  public Enrollment enrollment(CourseSection section, Student student, EnrollmentStatus status) {
    return mongo.save(
        Enrollment.builder()
            .sectionId(section.getId())
            .studentId(student.getId())
            .status(status)
            .enrolledAt(Instant.now())
            .build());
  }

  /** Xác nhận ban đầu của dữ liệu mới: chưa được ai xác nhận. */
  public static Verification unverified() {
    return Verification.builder().status(VerificationStatus.NEEDS_CLARIFICATION).build();
  }

  /** Một ObjectId hợp lệ nhưng chắc chắn không có trong CSDL. */
  public static String missingId() {
    return new ObjectId().toHexString();
  }

  // ---------------------------------------------------------------- dọn dẹp

  /**
   * Xóa mọi dữ liệu thuộc test này: quét theo tiền tố {@code runId} (cả bản ghi tạo qua API) rồi
   * xóa phần phụ thuộc theo phiên bản CTĐT và nhật ký kiểm toán của các user fixture.
   */
  public void cleanUp() {
    String codePrefix = "^ITEST-" + runId.toUpperCase() + "-";
    List<String> programIds = ids("programs", Criteria.where("code").regex(codePrefix));
    List<ObjectId> programOids = programIds.stream().map(ObjectId::new).toList();
    List<String> versionIds = ids("program_versions", Criteria.where("programId").in(programOids));
    List<ObjectId> versionOids = versionIds.stream().map(ObjectId::new).toList();

    removeIn("program_courses", "programVersionId", versionOids);
    removeIn("course_pi_contributions", "programVersionId", versionOids);
    removeIn("pis", "programVersionId", versionOids);
    removeIn("plos", "programVersionId", versionOids);
    removeIn("program_versions", "programId", programOids);
    mongo.remove(query(Criteria.where("code").regex(codePrefix)), "programs");
    List<ObjectId> courseOids =
        ids("courses", Criteria.where("code").regex(codePrefix)).stream()
            .map(ObjectId::new)
            .toList();
    removeIn("syllabi", "courseId", courseOids);
    mongo.remove(query(Criteria.where("code").regex(codePrefix)), "terms");
    mongo.remove(query(Criteria.where("code").regex(codePrefix)), "courses");
    List<ObjectId> sectionOids =
        ids("course_sections", Criteria.where("code").regex(codePrefix)).stream()
            .map(ObjectId::new)
            .toList();
    removeIn("enrollments", "sectionId", sectionOids);
    mongo.remove(query(Criteria.where("studentRef").regex(codePrefix)), "students");
    mongo.remove(query(Criteria.where("code").regex(codePrefix)), "course_sections");

    List<String> userIds = ids("users", Criteria.where("username").regex("^itest_" + runId + "_"));
    List<ObjectId> userOids = userIds.stream().map(ObjectId::new).toList();
    removeIn("audit_logs", "userId", userOids);
    mongo.remove(query(Criteria.where("username").regex("^itest_" + runId + "_")), "users");
    if (!extraUsernames.isEmpty()) {
      mongo.remove(query(Criteria.where("username").in(extraUsernames)), "users");
    }
  }

  private List<String> ids(String collection, Criteria criteria) {
    return mongo.find(query(criteria), org.bson.Document.class, collection).stream()
        .map(doc -> doc.getObjectId("_id").toHexString())
        .toList();
  }

  private void removeIn(String collection, String field, List<ObjectId> values) {
    if (!values.isEmpty()) {
      mongo.remove(query(Criteria.where(field).in(values)), collection);
    }
  }

  private static Query query(Criteria criteria) {
    return Query.query(criteria);
  }
}
