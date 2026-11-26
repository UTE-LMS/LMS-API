package com.hcmute.lms.lmsapi.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hcmute.lms.lmsapi.document.Assessment;
import com.hcmute.lms.lmsapi.document.AssessmentCloWeight;
import com.hcmute.lms.lmsapi.document.AssessmentItem;
import com.hcmute.lms.lmsapi.document.Clo;
import com.hcmute.lms.lmsapi.document.CloMeasurement;
import com.hcmute.lms.lmsapi.document.CloPiLink;
import com.hcmute.lms.lmsapi.document.Course;
import com.hcmute.lms.lmsapi.document.CourseSection;
import com.hcmute.lms.lmsapi.document.Enrollment;
import com.hcmute.lms.lmsapi.document.Faculty;
import com.hcmute.lms.lmsapi.document.ImportBatch;
import com.hcmute.lms.lmsapi.document.OutcomeMapping;
import com.hcmute.lms.lmsapi.document.Pi;
import com.hcmute.lms.lmsapi.document.Plo;
import com.hcmute.lms.lmsapi.document.Program;
import com.hcmute.lms.lmsapi.document.ProgramVersion;
import com.hcmute.lms.lmsapi.document.Score;
import com.hcmute.lms.lmsapi.document.Student;
import com.hcmute.lms.lmsapi.document.Syllabus;
import com.hcmute.lms.lmsapi.document.Term;
import com.hcmute.lms.lmsapi.document.Verification;
import com.hcmute.lms.lmsapi.dto.CalculationRunRequest;
import com.hcmute.lms.lmsapi.dto.ReportCreateRequest;
import com.hcmute.lms.lmsapi.enums.AssessmentPhase;
import com.hcmute.lms.lmsapi.enums.AssessmentScope;
import com.hcmute.lms.lmsapi.enums.AssessmentStatus;
import com.hcmute.lms.lmsapi.enums.AssessmentType;
import com.hcmute.lms.lmsapi.enums.CalculationScope;
import com.hcmute.lms.lmsapi.enums.EnrollmentStatus;
import com.hcmute.lms.lmsapi.enums.ImportBatchStatus;
import com.hcmute.lms.lmsapi.enums.ImportSourceSystem;
import com.hcmute.lms.lmsapi.enums.PloCategory;
import com.hcmute.lms.lmsapi.enums.ProgramVersionStatus;
import com.hcmute.lms.lmsapi.enums.ReimportPolicy;
import com.hcmute.lms.lmsapi.enums.ReportType;
import com.hcmute.lms.lmsapi.enums.Role;
import com.hcmute.lms.lmsapi.enums.SectionStatus;
import com.hcmute.lms.lmsapi.enums.SyllabusStatus;
import com.hcmute.lms.lmsapi.enums.VerificationStatus;
import com.hcmute.lms.lmsapi.repository.AggregateResultRepository;
import com.hcmute.lms.lmsapi.repository.AssessmentRepository;
import com.hcmute.lms.lmsapi.repository.CalculationRunRepository;
import com.hcmute.lms.lmsapi.repository.CloPiLinkRepository;
import com.hcmute.lms.lmsapi.repository.CloRepository;
import com.hcmute.lms.lmsapi.repository.CourseSectionRepository;
import com.hcmute.lms.lmsapi.repository.EnrollmentRepository;
import com.hcmute.lms.lmsapi.repository.ImportBatchRepository;
import com.hcmute.lms.lmsapi.repository.PiRepository;
import com.hcmute.lms.lmsapi.repository.PloRepository;
import com.hcmute.lms.lmsapi.repository.ProgramVersionRepository;
import com.hcmute.lms.lmsapi.repository.ReportRepository;
import com.hcmute.lms.lmsapi.repository.ScoreRepository;
import com.hcmute.lms.lmsapi.repository.StudentRepository;
import com.hcmute.lms.lmsapi.repository.StudentResultRepository;
import com.hcmute.lms.lmsapi.repository.SyllabusRepository;
import com.hcmute.lms.lmsapi.testsupport.IntegrationTestBase;
import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.assertj.core.api.Assertions;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

@DisplayName("F06 & F07: Engine đo lường 4 cấp độ, Báo cáo QA & Truy vết ngược")
class CalculationAndReportIntegrationTest extends IntegrationTestBase {

  @Autowired private ProgramVersionRepository programVersionRepository;
  @Autowired private PloRepository ploRepository;
  @Autowired private PiRepository piRepository;
  @Autowired private SyllabusRepository syllabusRepository;
  @Autowired private CloRepository cloRepository;
  @Autowired private CloPiLinkRepository cloPiLinkRepository;
  @Autowired private CourseSectionRepository courseSectionRepository;
  @Autowired private StudentRepository studentRepository;
  @Autowired private EnrollmentRepository enrollmentRepository;
  @Autowired private AssessmentRepository assessmentRepository;
  @Autowired private ImportBatchRepository importBatchRepository;
  @Autowired private ScoreRepository scoreRepository;
  @Autowired private CalculationRunRepository calculationRunRepository;
  @Autowired private StudentResultRepository studentResultRepository;
  @Autowired private AggregateResultRepository aggregateResultRepository;
  @Autowired private ReportRepository reportRepository;

  private String createdRunId;
  private String createdReportId;
  private final List<String> extraReportIds = new ArrayList<>();

  @AfterEach
  void cleanupCalculationArtifacts() {
    if (createdReportId != null) {
      reportRepository.deleteById(createdReportId);
      createdReportId = null;
    }
    for (String extraId : extraReportIds) {
      reportRepository.deleteById(extraId);
    }
    extraReportIds.clear();
    if (createdRunId != null) {
      studentResultRepository.deleteAll(studentResultRepository.findByRunId(createdRunId));
      aggregateResultRepository.deleteAll(aggregateResultRepository.findByRunId(createdRunId));
      calculationRunRepository.deleteById(createdRunId);
      createdRunId = null;
    }
  }

  @Test
  @DisplayName(
      "Chạy đo lường 4 cấp độ -> Sinh báo cáo QA -> Xuất CSV -> Truy vết về Score & ImportBatch")
  void fullCalculationReportAndTraceabilityFlow() throws Exception {
    Program program = fixtures.program();
    Course course = fixtures.course();
    Term term = fixtures.term();

    Verification verified =
        Verification.builder()
            .status(VerificationStatus.VERIFIED)
            .verifiedAt(Instant.now())
            .build();

    ProgramVersion pv =
        programVersionRepository.save(
            ProgramVersion.builder()
                .programId(program.getId())
                .version("2026-F06-" + System.nanoTime())
                .status(ProgramVersionStatus.ACTIVE)
                .verification(verified)
                .build());

    Plo plo =
        ploRepository.save(
            Plo.builder()
                .programVersionId(pv.getId())
                .code("PLO1")
                .contentOriginal("Kiến thức chuyên môn")
                .category(PloCategory.SPECIALIZED)
                .displayOrder(1)
                .passThreshold(0.6)
                .verification(verified)
                .build());

    Pi pi =
        piRepository.save(
            Pi.builder()
                .programVersionId(pv.getId())
                .ploId(plo.getId())
                .code("PI1.1")
                .contentOriginal("Phân tích yêu cầu")
                .displayOrder(1)
                .weight(1.0)
                .passThreshold(0.6)
                .verification(verified)
                .build());

    Syllabus syllabus =
        syllabusRepository.save(
            Syllabus.builder()
                .courseId(course.getId())
                .version("1.0")
                .status(SyllabusStatus.CONFIRMED)
                .verification(verified)
                .build());

    Clo clo =
        cloRepository.save(
            Clo.builder()
                .syllabusId(syllabus.getId())
                .courseId(course.getId())
                .code("CLO1")
                .contentOriginal("Thiết kế giải pháp")
                .bloomLevel(3)
                .measurement(CloMeasurement.builder().defaultPassThreshold(0.5).build())
                .build());

    cloPiLinkRepository.save(
        CloPiLink.builder()
            .programVersionId(pv.getId())
            .syllabusId(syllabus.getId())
            .cloId(clo.getId())
            .piId(pi.getId())
            .weight(1.0)
            .verification(verified)
            .build());

    CourseSection section =
        courseSectionRepository.save(
            CourseSection.builder()
                .code("SEC-F06-" + System.nanoTime())
                .courseId(course.getId())
                .syllabusId(syllabus.getId())
                .termId(term.getId())
                .enrolledCount(2)
                .status(SectionStatus.OPEN)
                .build());

    Student s1 =
        studentRepository.save(
            Student.builder()
                .studentCode("SV_F06_1_" + System.nanoTime())
                .fullName("Sinh viên F06 1")
                .cohort("K2024")
                .build());
    Student s2 =
        studentRepository.save(
            Student.builder()
                .studentCode("SV_F06_2_" + System.nanoTime())
                .fullName("Sinh viên F06 2")
                .cohort("K2024")
                .build());

    enrollmentRepository.save(
        Enrollment.builder()
            .sectionId(section.getId())
            .studentId(s1.getId())
            .status(EnrollmentStatus.ENROLLED)
            .enrolledAt(Instant.now())
            .build());
    enrollmentRepository.save(
        Enrollment.builder()
            .sectionId(section.getId())
            .studentId(s2.getId())
            .status(EnrollmentStatus.ENROLLED)
            .enrolledAt(Instant.now())
            .build());

    String itemId = new ObjectId().toHexString();
    Assessment assessment =
        assessmentRepository.save(
            Assessment.builder()
                .syllabusId(syllabus.getId())
                .sectionId(section.getId())
                .code("GK1")
                .name("Giữa kỳ")
                .scope(AssessmentScope.SECTION)
                .type(AssessmentType.MIDTERM)
                .phase(AssessmentPhase.MIDTERM)
                .status(AssessmentStatus.SCORE_ENTERED)
                .cloWeights(
                    List.of(AssessmentCloWeight.builder().cloId(clo.getId()).weight(1.0).build()))
                .items(
                    List.of(
                        AssessmentItem.builder()
                            .id(itemId)
                            .code("Câu 1")
                            .orderIndex(1)
                            .bloomLevel(3)
                            .outcomeMappings(
                                List.of(
                                    OutcomeMapping.builder()
                                        .cloId(clo.getId())
                                        .maxScore(10.0)
                                        .build()))
                            .build()))
                .build());

    ImportBatch batch =
        importBatchRepository.save(
            ImportBatch.builder()
                .sectionId(section.getId())
                .assessmentId(assessment.getId())
                .sourceSystem(ImportSourceSystem.CSV)
                .fileName("diem-gk1.csv")
                .fileChecksum("hash-" + System.nanoTime())
                .reimportPolicy(ReimportPolicy.REJECT_ON_CONFLICT)
                .status(ImportBatchStatus.CONFIRMED)
                .uploadedBy(new ObjectId().toHexString())
                .createdAt(Instant.now())
                .build());

    // s1: 8/10 = 0.8 (PASS >= 0.6), s2: thiếu điểm -> INSUFFICIENT_DATA
    Score score1 =
        scoreRepository.save(
            Score.builder()
                .sectionId(section.getId())
                .assessmentId(assessment.getId())
                .itemId(itemId)
                .cloId(clo.getId())
                .studentId(s1.getId())
                .rawScore(8.0)
                .maxScore(10.0)
                .importBatchId(batch.getId())
                .sourceRow(2)
                .updatedAt(Instant.now())
                .build());

    String qaToken = fixtures.bearerFor(Role.QA);
    String otherLecturerToken = fixtures.bearerFor(Role.LECTURER);

    CalculationRunRequest runReq =
        CalculationRunRequest.builder()
            .scope(CalculationScope.SECTION)
            .sectionIds(List.of(section.getId()))
            .programVersionId(pv.getId())
            .official(true)
            .build();

    // Giảng viên ngoài phạm vi -> 403
    mockMvc
        .perform(postAs(otherLecturerToken, "/api/v1/calculation-runs", runReq))
        .andExpect(status().isForbidden());

    // QA chạy tính toán 4 cấp độ -> 201 SUCCESS
    MvcResult runRes =
        mockMvc
            .perform(postAs(qaToken, "/api/v1/calculation-runs", runReq))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("SUCCESS"))
            .andExpect(jsonPath("$.programCode").value(program.getCode()))
            .andExpect(jsonPath("$.programName").value(program.getName()))
            .andExpect(jsonPath("$.programVersion").value(pv.getVersion()))
            .andExpect(jsonPath("$.termCode").value(term.getCode()))
            .andExpect(jsonPath("$.sectionCodes[0]").value(section.getCode()))
            .andExpect(jsonPath("$.courseCode").value(course.getCode()))
            .andExpect(jsonPath("$.courseName").value(course.getName()))
            .andExpect(jsonPath("$.studentResultCount").value(8))
            .andExpect(jsonPath("$.aggregateResultCount").value(4))
            .andReturn();
    createdRunId = JsonPath.read(runRes.getResponse().getContentAsString(), "$.id");

    // Kiểm tra danh sách & chi tiết lượt tính (đếm theo index/aggregation thay vì tải toàn bộ
    // kết quả)
    mockMvc
        .perform(
            getAs(
                qaToken,
                "/api/v1/calculation-runs?programVersionId={pvId}&page=0&size=20",
                pv.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].id").value(createdRunId))
        .andExpect(jsonPath("$.content[0].programCode").value(program.getCode()))
        .andExpect(jsonPath("$.content[0].programVersion").value(pv.getVersion()))
        .andExpect(jsonPath("$.content[0].termCode").value(term.getCode()))
        .andExpect(jsonPath("$.content[0].sectionCodes[0]").value(section.getCode()))
        .andExpect(jsonPath("$.content[0].courseCode").value(course.getCode()))
        .andExpect(jsonPath("$.content[0].studentResultCount").value(8))
        .andExpect(jsonPath("$.content[0].aggregateResultCount").value(4))
        .andExpect(jsonPath("$.totalElements").value(1));

    mockMvc
        .perform(getAs(qaToken, "/api/v1/calculation-runs/{id}", createdRunId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(createdRunId))
        .andExpect(jsonPath("$.programCode").value(program.getCode()))
        .andExpect(jsonPath("$.sectionCodes[0]").value(section.getCode()))
        .andExpect(jsonPath("$.studentResultCount").value(8))
        .andExpect(jsonPath("$.aggregateResultCount").value(4));

    mockMvc
        .perform(getAs(qaToken, "/api/v1/calculation-runs?size=0"))
        .andExpect(status().isBadRequest());

    // Kiểm tra aggregate ASSESSMENT_CLO (có đủ assessmentName/Type, sectionCode, courseCode/Name,
    // termCode, programCode/Version)
    mockMvc
        .perform(
            getAs(
                qaToken,
                "/api/v1/calculation-runs/{id}/aggregate-results?level=ASSESSMENT_CLO",
                createdRunId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].sectionCode").value(section.getCode()))
        .andExpect(jsonPath("$.content[0].courseCode").value(course.getCode()))
        .andExpect(jsonPath("$.content[0].courseName").value(course.getName()))
        .andExpect(jsonPath("$.content[0].termCode").value(term.getCode()))
        .andExpect(jsonPath("$.content[0].assessmentName").value("Giữa kỳ"))
        .andExpect(jsonPath("$.content[0].assessmentType").value("MIDTERM"))
        .andExpect(jsonPath("$.content[0].programCode").value(program.getCode()))
        .andExpect(jsonPath("$.content[0].programVersion").value(pv.getVersion()));

    // Kiểm tra student-results ASSESSMENT_CLO
    mockMvc
        .perform(
            getAs(
                qaToken,
                "/api/v1/calculation-runs/{id}/student-results?level=ASSESSMENT_CLO",
                createdRunId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].sectionCode").value(section.getCode()))
        .andExpect(jsonPath("$.content[0].courseCode").value(course.getCode()))
        .andExpect(jsonPath("$.content[0].termCode").value(term.getCode()))
        .andExpect(jsonPath("$.content[0].assessmentName").value("Giữa kỳ"))
        .andExpect(jsonPath("$.content[0].assessmentType").value("MIDTERM"))
        .andExpect(jsonPath("$.content[0].programCode").value(program.getCode()))
        .andExpect(jsonPath("$.content[0].programVersion").value(pv.getVersion()));

    // Kiểm tra aggregate PLO (1 passed / 2 enrolled -> passRateOfEnrolled = 0.5, dataCompleteness =
    // 0.5)
    mockMvc
        .perform(
            getAs(
                qaToken, "/api/v1/calculation-runs/{id}/aggregate-results?level=PLO", createdRunId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].subjectCode").value("PLO1"))
        .andExpect(jsonPath("$.content[0].programCode").value(program.getCode()))
        .andExpect(jsonPath("$.content[0].programVersion").value(pv.getVersion()))
        .andExpect(jsonPath("$.content[0].counts.enrolled").value(2))
        .andExpect(jsonPath("$.content[0].counts.passed").value(1))
        .andExpect(jsonPath("$.content[0].insufficientData").value(1))
        .andExpect(jsonPath("$.content[0].passRateOfEnrolled").value(0.5));

    // Phân trang: size ngoài biên [1, 100] bị từ chối 400
    mockMvc
        .perform(
            getAs(
                qaToken,
                "/api/v1/calculation-runs/{id}/aggregate-results?level=PLO&size=0",
                createdRunId))
        .andExpect(status().isBadRequest());

    // Sinh báo cáo PROGRAM_PLO cho QA
    ReportCreateRequest reportReq =
        ReportCreateRequest.builder().type(ReportType.PROGRAM_PLO).runId(createdRunId).build();
    MvcResult repRes =
        mockMvc
            .perform(postAs(qaToken, "/api/v1/reports", reportReq))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.type").value("PROGRAM_PLO"))
            .andExpect(
                jsonPath(
                    "$.limitations",
                    containsString("Chưa đủ dữ liệu để kết luận toàn chương trình")))
            .andReturn();
    createdReportId = JsonPath.read(repRes.getResponse().getContentAsString(), "$.id");

    // Xuất CSV báo cáo
    mockMvc
        .perform(postAs(qaToken, "/api/v1/reports/{id}/export", null, createdReportId))
        .andExpect(status().isOk())
        .andExpect(
            header()
                .string(
                    HttpHeaders.CONTENT_DISPOSITION,
                    containsString("report-" + createdReportId + ".csv")))
        .andExpect(content().string(containsString("PLO1")));

    // Xuất Excel .xlsx báo cáo -> ghi nhận thêm mục xlsx vào exports[]
    MvcResult xlsxRes =
        mockMvc
            .perform(
                postAs(qaToken, "/api/v1/reports/{id}/export?format=xlsx", null, createdReportId))
            .andExpect(status().isOk())
            .andExpect(
                header()
                    .string(
                        HttpHeaders.CONTENT_TYPE,
                        containsString(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")))
            .andExpect(
                header()
                    .string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        containsString("report-" + createdReportId + ".xlsx")))
            .andReturn();

    try (Workbook wb =
        new XSSFWorkbook(new ByteArrayInputStream(xlsxRes.getResponse().getContentAsByteArray()))) {
      Sheet sheet = wb.getSheet("BaoCao");
      Assertions.assertThat(sheet).isNotNull();
      Row ploRow = findRowContaining(sheet, "PLO", "PLO1");
      Assertions.assertThat(ploRow).isNotNull();
      Assertions.assertThat(ploRow.getCell(2).getNumericCellValue()).isEqualTo(2.0);
      Assertions.assertThat(ploRow.getCell(4).getNumericCellValue()).isEqualTo(1.0);
      Assertions.assertThat(ploRow.getCell(9).getNumericCellValue()).isEqualTo(0.5);
    }

    // Lịch sử exports[] ghi đủ cả csv và xlsx
    mockMvc
        .perform(getAs(qaToken, "/api/v1/reports/{id}", createdReportId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.exports", hasSize(2)))
        .andExpect(jsonPath("$.exports[0].format").value("csv"))
        .andExpect(jsonPath("$.exports[1].format").value("xlsx"));

    // Định dạng không hợp lệ bị từ chối 400
    mockMvc
        .perform(postAs(qaToken, "/api/v1/reports/{id}/export?format=pdf", null, createdReportId))
        .andExpect(status().isBadRequest());

    // Xuất Excel báo cáo DATA_COMPLETENESS chứa sinh viên thiếu điểm s2
    ReportCreateRequest compReq =
        ReportCreateRequest.builder()
            .type(ReportType.DATA_COMPLETENESS)
            .runId(createdRunId)
            .sectionId(section.getId())
            .build();
    MvcResult compRes =
        mockMvc
            .perform(postAs(qaToken, "/api/v1/reports", compReq))
            .andExpect(status().isCreated())
            .andReturn();
    String compReportId = JsonPath.read(compRes.getResponse().getContentAsString(), "$.id");
    extraReportIds.add(compReportId);

    MvcResult compXlsxRes =
        mockMvc
            .perform(postAs(qaToken, "/api/v1/reports/{id}/export?format=xlsx", null, compReportId))
            .andExpect(status().isOk())
            .andReturn();
    try (Workbook wb =
        new XSSFWorkbook(
            new ByteArrayInputStream(compXlsxRes.getResponse().getContentAsByteArray()))) {
      Sheet sheet = wb.getSheet("BaoCao");
      Assertions.assertThat(findRowContaining(sheet, s2.getStudentCode(), "CLO")).isNotNull();
    }

    // Truy vết ngược: PLO student_result -> PI -> CLO -> ASSESSMENT_CLO -> SCORE -> ImportBatch
    MvcResult ploSrRes =
        mockMvc
            .perform(
                getAs(
                    qaToken,
                    "/api/v1/calculation-runs/{id}/student-results?level=PLO",
                    createdRunId))
            .andExpect(status().isOk())
            .andReturn();
    String ploResultId =
        JsonPath.read(
                ploSrRes.getResponse().getContentAsString(),
                "$.content[?(@.status=='COMPUTED')].id")
            .toString()
            .replaceAll("[\\[\\]\"]", "");

    mockMvc
        .perform(getAs(qaToken, "/api/v1/trace/student-results/{id}", ploResultId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.level").value("PLO"))
        .andExpect(jsonPath("$.inputs", hasSize(1)))
        .andExpect(jsonPath("$.inputs[0].sourceType").value("STUDENT_RESULT"));

    mockMvc
        .perform(getAs(qaToken, "/api/v1/trace/scores/{id}", score1.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fileName").value("diem-gk1.csv"))
        .andExpect(jsonPath("$.sourceRow").value(2))
        .andExpect(jsonPath("$.rawScore").value(8.0));
  }

  @Test
  @DisplayName("Kiểm thử phân quyền tính toán & báo cáo: LECTURER (Phương án A) và PROGRAM_MANAGER")
  void strictScopeAuthorizationForLecturerAndProgramManager() throws Exception {
    String adminToken = fixtures.bearerFor(Role.ADMIN);

    com.hcmute.lms.lmsapi.document.User lecturer1 = fixtures.user(Role.LECTURER);
    com.hcmute.lms.lmsapi.document.User lecturer2 = fixtures.user(Role.LECTURER);
    String lec1Token = fixtures.bearer(lecturer1);
    String lec2Token = fixtures.bearer(lecturer2);

    Faculty fac1 = fixtures.faculty();
    Faculty fac2 = fixtures.faculty();
    com.hcmute.lms.lmsapi.document.User pm1 = fixtures.user(Role.PROGRAM_MANAGER);
    pm1.setFacultyId(fac1.getId());
    mongo().save(pm1);
    String pm1Token = fixtures.bearer(pm1);

    Program prog1 = fixtures.program();
    prog1.setFacultyId(fac1.getId());
    mongo().save(prog1);

    Program prog2 = fixtures.program();
    prog2.setFacultyId(fac2.getId());
    mongo().save(prog2);

    Verification verified =
        Verification.builder()
            .status(VerificationStatus.VERIFIED)
            .verifiedAt(Instant.now())
            .build();

    ProgramVersion pv1 =
        programVersionRepository.save(
            ProgramVersion.builder()
                .programId(prog1.getId())
                .version("2026-PV1-" + System.nanoTime())
                .status(ProgramVersionStatus.ACTIVE)
                .verification(verified)
                .build());

    ProgramVersion pv2 =
        programVersionRepository.save(
            ProgramVersion.builder()
                .programId(prog2.getId())
                .version("2026-PV2-" + System.nanoTime())
                .status(ProgramVersionStatus.ACTIVE)
                .verification(verified)
                .build());

    Course course1 = fixtures.course();
    Term term1 = fixtures.term();

    Syllabus syl1 =
        syllabusRepository.save(
            Syllabus.builder()
                .courseId(course1.getId())
                .version("1.0-" + System.nanoTime())
                .status(SyllabusStatus.CONFIRMED)
                .verification(verified)
                .build());

    CourseSection sec1 =
        courseSectionRepository.save(
            CourseSection.builder()
                .courseId(course1.getId())
                .syllabusId(syl1.getId())
                .termId(term1.getId())
                .code("SEC1-" + System.nanoTime())
                .status(SectionStatus.OPEN)
                .lecturerIds(List.of(lecturer1.getId()))
                .build());

    CourseSection sec2 =
        courseSectionRepository.save(
            CourseSection.builder()
                .courseId(course1.getId())
                .syllabusId(syl1.getId())
                .termId(term1.getId())
                .code("SEC2-" + System.nanoTime())
                .status(SectionStatus.OPEN)
                .lecturerIds(List.of(lecturer2.getId()))
                .build());

    // 1. LECTURER POST /api/v1/calculation-runs:
    // 1.1 Scope != SECTION -> 403 Forbidden
    CalculationRunRequest cohortReq =
        CalculationRunRequest.builder()
            .scope(CalculationScope.COHORT)
            .programVersionId(pv1.getId())
            .cohorts(List.of("K21"))
            .build();
    mockMvc
        .perform(postAs(lec1Token, "/api/v1/calculation-runs", cohortReq))
        .andExpect(status().isForbidden());

    // 1.2 Scope SECTION nhưng truyền programVersionId -> 403 Forbidden
    CalculationRunRequest secWithPvReq =
        CalculationRunRequest.builder()
            .scope(CalculationScope.SECTION)
            .sectionIds(List.of(sec1.getId()))
            .programVersionId(pv1.getId())
            .build();
    mockMvc
        .perform(postAs(lec1Token, "/api/v1/calculation-runs", secWithPvReq))
        .andExpect(status().isForbidden());

    // 1.3 Scope SECTION nhưng section thuộc về giảng viên khác -> 403 Forbidden
    CalculationRunRequest sec2Req =
        CalculationRunRequest.builder()
            .scope(CalculationScope.SECTION)
            .sectionIds(List.of(sec2.getId()))
            .build();
    mockMvc
        .perform(postAs(lec1Token, "/api/v1/calculation-runs", sec2Req))
        .andExpect(status().isForbidden());

    // 1.4 Scope SECTION với đúng lớp phụ trách -> 201 Created
    CalculationRunRequest sec1Req =
        CalculationRunRequest.builder()
            .scope(CalculationScope.SECTION)
            .sectionIds(List.of(sec1.getId()))
            .build();
    MvcResult lec1RunRes =
        mockMvc
            .perform(postAs(lec1Token, "/api/v1/calculation-runs", sec1Req))
            .andExpect(status().isCreated())
            .andReturn();
    String lec1RunId = JsonPath.read(lec1RunRes.getResponse().getContentAsString(), "$.id");

    // 2. LECTURER GET /api/v1/calculation-runs: chỉ thấy các run thuộc lớp mình phụ trách
    // Admin chạy 1 run cho sec2
    MvcResult adminRunRes =
        mockMvc
            .perform(postAs(adminToken, "/api/v1/calculation-runs", sec2Req))
            .andExpect(status().isCreated())
            .andReturn();
    String adminRunId = JsonPath.read(adminRunRes.getResponse().getContentAsString(), "$.id");

    mockMvc
        .perform(getAs(lec1Token, "/api/v1/calculation-runs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[?(@.id == '" + lec1RunId + "')]").exists())
        .andExpect(jsonPath("$.content[?(@.id == '" + adminRunId + "')]").doesNotExist());

    mockMvc
        .perform(getAs(lec2Token, "/api/v1/calculation-runs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[?(@.id == '" + lec1RunId + "')]").doesNotExist())
        .andExpect(jsonPath("$.content[?(@.id == '" + adminRunId + "')]").exists());

    // 3. LECTURER GET student-results / aggregate-results:
    // Cố tình xem PLO/PI -> 403 Forbidden
    mockMvc
        .perform(
            getAs(lec1Token, "/api/v1/calculation-runs/{id}/student-results?level=PLO", lec1RunId))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            getAs(lec1Token, "/api/v1/calculation-runs/{id}/aggregate-results?level=PI", lec1RunId))
        .andExpect(status().isForbidden());

    // Xem lớp của giảng viên khác -> 403 Forbidden
    mockMvc
        .perform(
            getAs(
                lec1Token,
                "/api/v1/calculation-runs/{id}/student-results?level=CLO&sectionId={secId}",
                adminRunId,
                sec2.getId()))
        .andExpect(status().isForbidden());

    // 4. LECTURER Báo cáo:
    // 4.1 Báo cáo COURSE_PI / PROGRAM_PLO -> 403 Forbidden
    ReportCreateRequest ploReportReq =
        ReportCreateRequest.builder().type(ReportType.PROGRAM_PLO).runId(lec1RunId).build();
    mockMvc
        .perform(postAs(lec1Token, "/api/v1/reports", ploReportReq))
        .andExpect(status().isForbidden());

    // 4.2 Báo cáo SECTION_CLO với lớp của giảng viên khác -> 403 Forbidden
    ReportCreateRequest sec2ReportReq =
        ReportCreateRequest.builder()
            .type(ReportType.SECTION_CLO)
            .runId(adminRunId)
            .sectionId(sec2.getId())
            .build();
    mockMvc
        .perform(postAs(lec1Token, "/api/v1/reports", sec2ReportReq))
        .andExpect(status().isForbidden());

    // 4.3 Báo cáo SECTION_CLO với lớp mình phụ trách -> 201 Created
    ReportCreateRequest sec1ReportReq =
        ReportCreateRequest.builder()
            .type(ReportType.SECTION_CLO)
            .runId(lec1RunId)
            .sectionId(sec1.getId())
            .build();
    MvcResult repRes =
        mockMvc
            .perform(postAs(lec1Token, "/api/v1/reports", sec1ReportReq))
            .andExpect(status().isCreated())
            .andReturn();
    String report1Id = JsonPath.read(repRes.getResponse().getContentAsString(), "$.id");
    extraReportIds.add(report1Id);

    // 5. PROGRAM_MANAGER kiểm soát theo Khoa:
    // PM khoa 1 cố tình chạy tính toán trên CTĐT khoa 2 -> 403 Forbidden
    CalculationRunRequest foreignPmReq =
        CalculationRunRequest.builder()
            .scope(CalculationScope.PROGRAM)
            .programVersionId(pv2.getId())
            .build();
    mockMvc
        .perform(postAs(pm1Token, "/api/v1/calculation-runs", foreignPmReq))
        .andExpect(status().isForbidden());

    // Cleanup extra runs
    calculationRunRepository.deleteById(lec1RunId);
    calculationRunRepository.deleteById(adminRunId);
  }

  private static Row findRowContaining(Sheet sheet, String col0, String col1) {
    for (Row row : sheet) {
      Cell c0 = row.getCell(0);
      Cell c1 = row.getCell(1);
      if (c0 != null
          && c1 != null
          && c0.getCellType() == CellType.STRING
          && c1.getCellType() == CellType.STRING
          && col0.equals(c0.getStringCellValue())
          && col1.equals(c1.getStringCellValue())) {
        return row;
      }
    }
    return null;
  }
}
