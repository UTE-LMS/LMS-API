package com.hcmute.lms.lmsapi.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hcmute.lms.lmsapi.document.CourseSection;
import com.hcmute.lms.lmsapi.document.Enrollment;
import com.hcmute.lms.lmsapi.document.Student;
import com.hcmute.lms.lmsapi.document.Term;
import com.hcmute.lms.lmsapi.dto.CsvImportResultResponse;
import com.hcmute.lms.lmsapi.dto.EnrollmentImportResponse;
import com.hcmute.lms.lmsapi.dto.EnrollmentListResponse;
import com.hcmute.lms.lmsapi.dto.EnrollmentResponse;
import com.hcmute.lms.lmsapi.dto.EnrollmentStatusUpdateRequest;
import com.hcmute.lms.lmsapi.dto.FieldErrorDetail;
import com.hcmute.lms.lmsapi.enums.EnrollmentStatus;
import com.hcmute.lms.lmsapi.enums.SectionStatus;
import com.hcmute.lms.lmsapi.enums.StudentStatus;
import com.hcmute.lms.lmsapi.enums.TermStatus;
import com.hcmute.lms.lmsapi.exception.BusinessRuleViolationException;
import com.hcmute.lms.lmsapi.exception.ImportValidationException;
import com.hcmute.lms.lmsapi.exception.ResourceNotFoundException;
import com.hcmute.lms.lmsapi.mapper.impl.EnrollmentMapperImpl;
import com.hcmute.lms.lmsapi.repository.CourseSectionRepository;
import com.hcmute.lms.lmsapi.repository.EnrollmentRepository;
import com.hcmute.lms.lmsapi.repository.StudentRepository;
import com.hcmute.lms.lmsapi.repository.TermRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class EnrollmentServiceImplTest {

  private static final String SECTION_ID = "671dcba5f123456789abc501";
  private static final String TERM_ID = "671dcba5f123456789abc301";
  private static final String ENROLLMENT_ID = "671dcba5f123456789abc601";
  private static final String STUDENT_ID = "671dcba5f123456789abc701";

  @Mock private EnrollmentRepository enrollmentRepository;
  @Mock private StudentRepository studentRepository;
  @Mock private CourseSectionRepository sectionRepository;
  @Mock private TermRepository termRepository;

  private EnrollmentServiceImpl service;

  @BeforeEach
  void setUp() {
    service =
        new EnrollmentServiceImpl(
            enrollmentRepository,
            studentRepository,
            sectionRepository,
            termRepository,
            new EnrollmentMapperImpl());
  }

  private CourseSection section(SectionStatus status) {
    return CourseSection.builder()
        .id(SECTION_ID)
        .code("SEC-01")
        .termId(TERM_ID)
        .status(status)
        .enrolledCount(2)
        .build();
  }

  private Enrollment enrollment(EnrollmentStatus status) {
    return Enrollment.builder()
        .id(ENROLLMENT_ID)
        .sectionId(SECTION_ID)
        .studentId(STUDENT_ID)
        .status(status)
        .build();
  }

  private void stubOpenTerm() {
    when(termRepository.findById(TERM_ID))
        .thenReturn(
            Optional.of(
                Term.builder().id(TERM_ID).code("2026-HK1").status(TermStatus.OPEN).build()));
  }

  private EnrollmentStatusUpdateRequest request(EnrollmentStatus status) {
    return EnrollmentStatusUpdateRequest.builder().status(status).build();
  }

  @Test
  @DisplayName("US-20/AC-4: rút khỏi lớp -> WITHDRAWN và sĩ số được tính lại từ enrollments")
  void withdraw_recountsEnrolled() {
    CourseSection section = section(SectionStatus.OPEN);
    Enrollment enrollment = enrollment(EnrollmentStatus.ENROLLED);
    when(enrollmentRepository.findById(ENROLLMENT_ID)).thenReturn(Optional.of(enrollment));
    when(sectionRepository.findById(SECTION_ID)).thenReturn(Optional.of(section));
    when(studentRepository.findById(STUDENT_ID))
        .thenReturn(Optional.of(Student.builder().id(STUDENT_ID).studentCode("SV-1").build()));
    stubOpenTerm();
    when(enrollmentRepository.save(any(Enrollment.class))).thenAnswer(i -> i.getArgument(0));
    when(enrollmentRepository.countBySectionIdAndStatus(SECTION_ID, EnrollmentStatus.ENROLLED))
        .thenReturn(1L);

    EnrollmentResponse response =
        service.updateStatus(ENROLLMENT_ID, request(EnrollmentStatus.WITHDRAWN), "actor");

    assertThat(response.getStatus()).isEqualTo(EnrollmentStatus.WITHDRAWN);
    assertThat(response.getStudentCode()).isEqualTo("SV-1");
    ArgumentCaptor<CourseSection> saved = ArgumentCaptor.forClass(CourseSection.class);
    verify(sectionRepository).save(saved.capture());
    assertThat(saved.getValue().getEnrolledCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("US-20/AC-4: ghi danh lại -> ENROLLED, enrolledAt được đặt lại")
  void reEnroll_setsEnrolledAt() {
    CourseSection section = section(SectionStatus.OPEN);
    Enrollment enrollment = enrollment(EnrollmentStatus.WITHDRAWN);
    when(enrollmentRepository.findById(ENROLLMENT_ID)).thenReturn(Optional.of(enrollment));
    when(sectionRepository.findById(SECTION_ID)).thenReturn(Optional.of(section));
    when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.empty());
    stubOpenTerm();
    when(enrollmentRepository.save(any(Enrollment.class))).thenAnswer(i -> i.getArgument(0));
    when(enrollmentRepository.countBySectionIdAndStatus(SECTION_ID, EnrollmentStatus.ENROLLED))
        .thenReturn(3L);

    EnrollmentResponse response =
        service.updateStatus(ENROLLMENT_ID, request(EnrollmentStatus.ENROLLED), "actor");

    assertThat(response.getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
    assertThat(response.getEnrolledAt()).isNotNull();
    assertThat(section.getEnrolledCount()).isEqualTo(3);
  }

  @Test
  @DisplayName("US-20: đổi sang đúng trạng thái hiện tại là no-op, không ghi, không tính lại")
  void sameStatus_isNoOp() {
    when(enrollmentRepository.findById(ENROLLMENT_ID))
        .thenReturn(Optional.of(enrollment(EnrollmentStatus.ENROLLED)));
    when(sectionRepository.findById(SECTION_ID))
        .thenReturn(Optional.of(section(SectionStatus.LOCKED)));
    when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.empty());

    EnrollmentResponse response =
        service.updateStatus(ENROLLMENT_ID, request(EnrollmentStatus.ENROLLED), "actor");

    assertThat(response.getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
    verify(enrollmentRepository, never()).save(any());
    verify(sectionRepository, never()).save(any());
  }

  @Test
  @DisplayName("US-20: lớp CLOSED/LOCKED hoặc học kỳ không OPEN -> 422, không ghi")
  void frozen_rejected() {
    when(enrollmentRepository.findById(ENROLLMENT_ID))
        .thenReturn(Optional.of(enrollment(EnrollmentStatus.ENROLLED)));
    when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.empty());

    for (SectionStatus frozen : List.of(SectionStatus.CLOSED, SectionStatus.LOCKED)) {
      when(sectionRepository.findById(SECTION_ID)).thenReturn(Optional.of(section(frozen)));
      assertThatThrownBy(
              () -> service.updateStatus(ENROLLMENT_ID, request(EnrollmentStatus.WITHDRAWN), "a"))
          .isInstanceOf(BusinessRuleViolationException.class)
          .hasMessageContaining(frozen.name());
    }

    when(sectionRepository.findById(SECTION_ID))
        .thenReturn(Optional.of(section(SectionStatus.OPEN)));
    when(termRepository.findById(TERM_ID))
        .thenReturn(
            Optional.of(
                Term.builder()
                    .id(TERM_ID)
                    .code("2026-HK1")
                    .status(TermStatus.SCORE_LOCKED)
                    .build()));
    assertThatThrownBy(
            () -> service.updateStatus(ENROLLMENT_ID, request(EnrollmentStatus.WITHDRAWN), "a"))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessageContaining("SCORE_LOCKED");

    when(termRepository.findById(TERM_ID)).thenReturn(Optional.empty());
    assertThatThrownBy(
            () -> service.updateStatus(ENROLLMENT_ID, request(EnrollmentStatus.WITHDRAWN), "a"))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessageContaining("Học kỳ của lớp không tồn tại");

    verify(enrollmentRepository, never()).save(any());
  }

  @Test
  @DisplayName("updateStatus: ghi danh không tồn tại -> 404; ID sai định dạng -> lỗi validate")
  void updateStatus_missingEnrollment() {
    when(enrollmentRepository.findById(ENROLLMENT_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> service.updateStatus(ENROLLMENT_ID, request(EnrollmentStatus.WITHDRAWN), "a"))
        .isInstanceOf(ResourceNotFoundException.class);
    assertThatThrownBy(() -> service.updateStatus("bad", request(EnrollmentStatus.WITHDRAWN), "a"))
        .isInstanceOf(com.hcmute.lms.lmsapi.exception.AppException.class);
  }

  @Test
  @DisplayName(
      "US-20: getEnrollments đếm ENROLLED/WITHDRAWN toàn lớp, lọc theo status, sắp theo mã")
  void getEnrollments_countsAndFilters() {
    when(sectionRepository.findById(SECTION_ID))
        .thenReturn(Optional.of(section(SectionStatus.OPEN)));
    Enrollment e1 = enrollment(EnrollmentStatus.ENROLLED);
    e1.setStudentId("671dcba5f123456789abc701");
    Enrollment e2 = enrollment(EnrollmentStatus.ENROLLED);
    e2.setId("671dcba5f123456789abc602");
    e2.setStudentId("671dcba5f123456789abc702");
    when(enrollmentRepository.findBySectionIdAndStatus(SECTION_ID, EnrollmentStatus.ENROLLED))
        .thenReturn(List.of(e1, e2));
    when(studentRepository.findAllById(
            List.of("671dcba5f123456789abc701", "671dcba5f123456789abc702")))
        .thenReturn(
            List.of(
                Student.builder().id("671dcba5f123456789abc702").studentCode("SV-A").build(),
                Student.builder().id("671dcba5f123456789abc701").studentCode("SV-B").build()));
    when(enrollmentRepository.countBySectionIdAndStatus(SECTION_ID, EnrollmentStatus.ENROLLED))
        .thenReturn(2L);
    when(enrollmentRepository.countBySectionIdAndStatus(SECTION_ID, EnrollmentStatus.WITHDRAWN))
        .thenReturn(1L);

    EnrollmentListResponse response =
        service.getEnrollments(SECTION_ID, EnrollmentStatus.ENROLLED, null, 0, 20);

    assertThat(response.getEnrolled()).isEqualTo(2);
    assertThat(response.getWithdrawn()).isEqualTo(1);
    assertThat(response.getTotalElements()).isEqualTo(2);
    assertThat(response.getTotalPages()).isEqualTo(1);
    assertThat(response.isLast()).isTrue();
    assertThat(response.getEnrollments())
        .extracting(EnrollmentResponse::getStudentCode)
        .containsExactly("SV-A", "SV-B");

    EnrollmentListResponse searched =
        service.getEnrollments(SECTION_ID, EnrollmentStatus.ENROLLED, "sv-b", 0, 20);
    assertThat(searched.getTotalElements()).isEqualTo(1);
    assertThat(searched.getEnrollments())
        .extracting(EnrollmentResponse::getStudentCode)
        .containsExactly("SV-B");
  }

  private java.io.InputStream csv(String content) {
    return new java.io.ByteArrayInputStream(
        content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  @Test
  @DisplayName("US-20/AC-1: nhập tệp -> ghi danh sinh viên đã tồn tại, không tạo sinh viên mới")
  void importEnrollments_enrollsExistingStudentsWithoutCreatingNew() {
    CourseSection section = section(SectionStatus.OPEN);
    when(sectionRepository.findById(SECTION_ID)).thenReturn(Optional.of(section));
    stubOpenTerm();
    Student s1 = Student.builder().id(STUDENT_ID).studentCode("SV-1").build();
    Student s2 = Student.builder().id("671dcba5f123456789abc702").studentCode("SV-2").build();
    when(studentRepository.findByStudentCodeIn(List.of("SV-1", "SV-2")))
        .thenReturn(List.of(s1, s2));
    when(enrollmentRepository.findBySectionId(SECTION_ID)).thenReturn(List.of());
    when(enrollmentRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));
    when(enrollmentRepository.countBySectionIdAndStatus(SECTION_ID, EnrollmentStatus.ENROLLED))
        .thenReturn(2L);

    EnrollmentImportResponse response =
        service.importEnrollments(SECTION_ID, csv("studentCode,cohort\nSV-1,K22\nSV-2,K23\n"), "a");

    assertThat(response.getTotalRows()).isEqualTo(2);
    assertThat(response.getNewStudents()).isZero();
    assertThat(response.getReusedStudents()).isEqualTo(2);
    assertThat(response.getSectionEnrolledCount()).isEqualTo(2);
    verify(studentRepository, never()).saveAll(any());
    verify(studentRepository, never()).save(any());
  }

  @Test
  @DisplayName(
      "US-20/AC-2: mã sinh viên chưa tồn tại trong hệ thống -> 422 theo dòng, không ghi gì")
  void importEnrollments_rejectsUnknownStudentCode() {
    when(sectionRepository.findById(SECTION_ID))
        .thenReturn(Optional.of(section(SectionStatus.OPEN)));
    stubOpenTerm();
    Student existing = Student.builder().id(STUDENT_ID).studentCode("SV-1").build();
    when(studentRepository.findByStudentCodeIn(List.of("SV-1", "SV-MISSING")))
        .thenReturn(List.of(existing));
    when(enrollmentRepository.findBySectionId(SECTION_ID)).thenReturn(List.of());

    assertThatThrownBy(
            () ->
                service.importEnrollments(SECTION_ID, csv("studentCode\nSV-1\nSV-MISSING\n"), "a"))
        .isInstanceOfSatisfying(
            ImportValidationException.class,
            ex -> {
              assertThat(ex.getRowErrors())
                  .extracting(FieldErrorDetail::getField)
                  .containsExactly("line 3");
              assertThat(ex.getRowErrors().get(0).getMessage()).contains("không tồn tại");
            });

    verify(studentRepository, never()).saveAll(any());
    verify(enrollmentRepository, never()).saveAll(any());
    verify(sectionRepository, never()).save(any());
  }

  @Test
  @DisplayName(
      "US-20/AC-2: gom đủ mọi lỗi theo dòng (thiếu, sai mẫu, quá dài, trùng, chưa có SV, đã ghi danh, đã rút), không ghi gì")
  void importEnrollments_collectsAllRowErrorsAndWritesNothing() {
    when(sectionRepository.findById(SECTION_ID))
        .thenReturn(Optional.of(section(SectionStatus.OPEN)));
    stubOpenTerm();
    Student sv2 = Student.builder().id("s0").studentCode("SV-2").build();
    Student enrolledStudent = Student.builder().id("s1").studentCode("SV-IN").build();
    Student withdrawnStudent = Student.builder().id("s2").studentCode("SV-OUT").build();
    when(studentRepository.findByStudentCodeIn(any()))
        .thenReturn(List.of(sv2, enrolledStudent, withdrawnStudent));
    when(enrollmentRepository.findBySectionId(SECTION_ID))
        .thenReturn(
            List.of(
                Enrollment.builder()
                    .id("e1")
                    .studentId("s1")
                    .status(EnrollmentStatus.ENROLLED)
                    .build(),
                Enrollment.builder()
                    .id("e2")
                    .studentId("s2")
                    .status(EnrollmentStatus.WITHDRAWN)
                    .build()));
    String tooLong = "x".repeat(51);

    String content =
        "studentCode,cohort\n"
            + "\"\",K22\n" // line 2: thiếu
            + "BAD REF,K22\n" // line 3: sai mẫu
            + "SV-1,"
            + tooLong
            + "\n" // line 4: cohort quá dài
            + "SV-2,K22\n" // line 5: hợp lệ
            + "SV-2,K22\n" // line 6: trùng dòng 5
            + "SV-IN,K22\n" // line 7: đã ghi danh
            + "SV-OUT,K22\n" // line 8: đã rút
            + "SV-NOPE,K22\n"; // line 9: chưa tồn tại trong hệ thống

    assertThatThrownBy(() -> service.importEnrollments(SECTION_ID, csv(content), "a"))
        .isInstanceOfSatisfying(
            ImportValidationException.class,
            ex ->
                assertThat(ex.getRowErrors())
                    .extracting(FieldErrorDetail::getField)
                    .containsExactly(
                        "line 2", "line 3", "line 4", "line 6", "line 7", "line 8", "line 9"));

    verify(studentRepository, never()).saveAll(any());
    verify(enrollmentRepository, never()).saveAll(any());
    verify(sectionRepository, never()).save(any());
  }

  @Test
  @DisplayName("importEnrollmentsBulk: từ chối mã sinh viên chưa tồn tại và không tự tạo mới")
  void importEnrollmentsBulk_rejectsUnknownStudentAndNeverCreatesStudent() {
    CourseSection section = section(SectionStatus.OPEN);
    when(sectionRepository.findByCode("SEC-01")).thenReturn(Optional.of(section));
    stubOpenTerm();
    Student existing = Student.builder().id(STUDENT_ID).studentCode("SV-1").build();
    when(studentRepository.findByStudentCode("SV-1")).thenReturn(Optional.of(existing));
    when(studentRepository.findByStudentCode("SV-MISSING")).thenReturn(Optional.empty());
    when(enrollmentRepository.save(any(Enrollment.class))).thenAnswer(i -> i.getArgument(0));
    when(enrollmentRepository.countBySectionIdAndStatus(SECTION_ID, EnrollmentStatus.ENROLLED))
        .thenReturn(1L);

    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "bulk.csv",
            "text/csv",
            "sectionCode,studentCode,cohort\nSEC-01,SV-1,K23\nSEC-01,SV-MISSING,K23\n"
                .getBytes(StandardCharsets.UTF_8));

    CsvImportResultResponse result = service.importEnrollmentsBulk(file, null);

    assertThat(result.getTotalRows()).isEqualTo(2);
    assertThat(result.getSuccessCount()).isEqualTo(1);
    assertThat(result.getFailureCount()).isEqualTo(1);
    assertThat(result.getErrors().get(0).getRow()).isEqualTo(3);
    assertThat(result.getErrors().get(0).getKey()).isEqualTo("SV-MISSING");
    assertThat(result.getErrors().get(0).getMessage()).contains("không tồn tại");
    verify(studentRepository, never()).save(any());
    verify(studentRepository, never()).saveAll(any());
  }

  @Test
  @DisplayName("US-20: lớp không tồn tại -> 404; lớp đóng băng -> 422 trước khi đọc tệp")
  void importEnrollments_sectionRules() {
    when(sectionRepository.findById(SECTION_ID)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.importEnrollments(SECTION_ID, csv("studentCode\nSV-1\n"), "a"))
        .isInstanceOf(ResourceNotFoundException.class);

    when(sectionRepository.findById(SECTION_ID))
        .thenReturn(Optional.of(section(SectionStatus.LOCKED)));
    assertThatThrownBy(() -> service.importEnrollments(SECTION_ID, csv("not even a csv"), "a"))
        .isInstanceOf(BusinessRuleViolationException.class);
    verify(studentRepository, never()).findByStudentCodeIn(any());
  }

  @Test
  @DisplayName("getEnrollments: lớp không tồn tại -> 404")
  void getEnrollments_missingSection() {
    when(sectionRepository.findById(SECTION_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getEnrollments(SECTION_ID, null, null, 0, 20))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  @DisplayName("Chặn ghi danh sinh viên khác ACTIVE ở import 1 lớp, bulk import và ghi danh lại")
  void nonActiveStudents_rejectedOnImportAndReEnroll_butAllowedToWithdraw() {
    CourseSection section = section(SectionStatus.OPEN);
    when(sectionRepository.findById(SECTION_ID)).thenReturn(Optional.of(section));
    stubOpenTerm();

    Student suspended =
        Student.builder()
            .id(STUDENT_ID)
            .studentCode("SV-SUS")
            .status(StudentStatus.SUSPENDED)
            .build();
    Student active =
        Student.builder()
            .id("671dcba5f123456789abc702")
            .studentCode("SV-ACT")
            .status(StudentStatus.ACTIVE)
            .build();
    when(studentRepository.findByStudentCodeIn(List.of("SV-SUS"))).thenReturn(List.of(suspended));
    when(enrollmentRepository.findBySectionId(SECTION_ID)).thenReturn(List.of());

    assertThatThrownBy(
            () -> service.importEnrollments(SECTION_ID, csv("studentCode\nSV-SUS\n"), "a"))
        .isInstanceOfSatisfying(
            ImportValidationException.class,
            ex -> {
              assertThat(ex.getRowErrors()).hasSize(1);
              assertThat(ex.getRowErrors().get(0).getMessage()).contains("ACTIVE");
            });

    when(sectionRepository.findByCode("SEC-01")).thenReturn(Optional.of(section));
    when(studentRepository.findByStudentCode("SV-ACT")).thenReturn(Optional.of(active));
    when(studentRepository.findByStudentCode("SV-SUS")).thenReturn(Optional.of(suspended));
    when(enrollmentRepository.save(any(Enrollment.class))).thenAnswer(i -> i.getArgument(0));
    when(enrollmentRepository.countBySectionIdAndStatus(SECTION_ID, EnrollmentStatus.ENROLLED))
        .thenReturn(1L);

    MockMultipartFile bulkFile =
        new MockMultipartFile(
            "file",
            "bulk.csv",
            "text/csv",
            "sectionCode,studentCode,cohort\nSEC-01,SV-ACT,K23\nSEC-01,SV-SUS,K23\n"
                .getBytes(StandardCharsets.UTF_8));
    CsvImportResultResponse bulkResult = service.importEnrollmentsBulk(bulkFile, null);
    assertThat(bulkResult.getSuccessCount()).isEqualTo(1);
    assertThat(bulkResult.getFailureCount()).isEqualTo(1);
    assertThat(bulkResult.getErrors().get(0).getMessage()).contains("ACTIVE");

    Enrollment withdrawn = enrollment(EnrollmentStatus.WITHDRAWN);
    when(enrollmentRepository.findById(ENROLLMENT_ID)).thenReturn(Optional.of(withdrawn));
    when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(suspended));
    assertThatThrownBy(
            () -> service.updateStatus(ENROLLMENT_ID, request(EnrollmentStatus.ENROLLED), "a"))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessageContaining("SUSPENDED");

    Enrollment enrolled = enrollment(EnrollmentStatus.ENROLLED);
    when(enrollmentRepository.findById(ENROLLMENT_ID)).thenReturn(Optional.of(enrolled));
    EnrollmentResponse withdrawRes =
        service.updateStatus(ENROLLMENT_ID, request(EnrollmentStatus.WITHDRAWN), "a");
    assertThat(withdrawRes.getStatus()).isEqualTo(EnrollmentStatus.WITHDRAWN);
  }
}
