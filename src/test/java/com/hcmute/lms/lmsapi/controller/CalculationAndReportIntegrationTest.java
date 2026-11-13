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
import com.hcmute.lms.lmsapi.document.UserScope;
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
import com.hcmute.lms.lmsapi.document.Verification;
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
import java.time.Instant;
import java.util.List;
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

  @AfterEach
  void cleanupCalculationArtifacts() {
    if (createdReportId != null) {
      reportRepository.deleteById(createdReportId);
      createdReportId = null;
    }
    if (createdRunId != null) {
      studentResultRepository.deleteAll(studentResultRepository.findByRunId(createdRunId));
      aggregateResultRepository.deleteAll(aggregateResultRepository.findByRunId(createdRunId));
      calculationRunRepository.deleteById(createdRunId);
      createdRunId = null;
    }
  }

  @Test
  @DisplayName("Chạy đo lường 4 cấp độ -> Sinh báo cáo QA -> Xuất CSV -> Truy vết về Score & ImportBatch")
  void fullCalculationReportAndTraceabilityFlow() throws Exception {
    Program program = fixtures.program();
    Course course = fixtures.course();
    Term term = fixtures.term();

    Verification verified =
        Verification.builder().status(VerificationStatus.VERIFIED).verifiedAt(Instant.now()).build();

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
            Student.builder().studentRef("SV_F06_1_" + System.nanoTime()).cohort("K2024").build());
    Student s2 =
        studentRepository.save(
            Student.builder().studentRef("SV_F06_2_" + System.nanoTime()).cohort("K2024").build());

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
                .locked(true)
                .cloWeights(List.of(AssessmentCloWeight.builder().cloId(clo.getId()).weight(1.0).build()))
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

    String qaToken =
        fixtures.bearerFor(
            Role.QA,
            UserScope.builder()
                .programIds(List.of(program.getId()))
                .courseIds(List.of(course.getId()))
                .build());
    String otherLecturerToken =
        fixtures.bearerFor(Role.LECTURER, UserScope.builder().build());

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
            .andExpect(jsonPath("$.studentResultCount").value(8))
            .andExpect(jsonPath("$.aggregateResultCount").value(4))
            .andReturn();
    createdRunId = JsonPath.read(runRes.getResponse().getContentAsString(), "$.id");

    // Kiểm tra aggregate PLO (1 passed / 2 enrolled -> passRateOfEnrolled = 0.5, dataCompleteness = 0.5)
    mockMvc
        .perform(
            getAs(
                qaToken,
                "/api/v1/calculation-runs/{id}/aggregate-results?level=PLO",
                createdRunId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].subjectCode").value("PLO1"))
        .andExpect(jsonPath("$[0].counts.enrolled").value(2))
        .andExpect(jsonPath("$[0].counts.passed").value(1))
        .andExpect(jsonPath("$[0].insufficientData").value(1))
        .andExpect(jsonPath("$[0].passRateOfEnrolled").value(0.5));

    // Sinh báo cáo PROGRAM_PLO cho QA
    ReportCreateRequest reportReq =
        ReportCreateRequest.builder()
            .type(ReportType.PROGRAM_PLO)
            .runId(createdRunId)
            .build();
    MvcResult repRes =
        mockMvc
            .perform(postAs(qaToken, "/api/v1/reports", reportReq))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.type").value("PROGRAM_PLO"))
            .andExpect(jsonPath("$.limitations", containsString("Chưa đủ dữ liệu để kết luận toàn chương trình")))
            .andReturn();
    createdReportId = JsonPath.read(repRes.getResponse().getContentAsString(), "$.id");

    // Xuất CSV báo cáo
    mockMvc
        .perform(postAs(qaToken, "/api/v1/reports/{id}/export", null, createdReportId))
        .andExpect(status().isOk())
        .andExpect(
            header()
                .string(HttpHeaders.CONTENT_DISPOSITION, containsString("report-" + createdReportId + ".csv")))
        .andExpect(content().string(containsString("PLO1")));

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
            ploSrRes.getResponse().getContentAsString(), "$[?(@.status=='COMPUTED')].id")
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
}
