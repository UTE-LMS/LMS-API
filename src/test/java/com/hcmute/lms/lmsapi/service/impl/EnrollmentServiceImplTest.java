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
import com.hcmute.lms.lmsapi.dto.EnrollmentImportResponse;
import com.hcmute.lms.lmsapi.dto.EnrollmentListResponse;
import com.hcmute.lms.lmsapi.dto.EnrollmentResponse;
import com.hcmute.lms.lmsapi.dto.EnrollmentStatusUpdateRequest;
import com.hcmute.lms.lmsapi.dto.FieldErrorDetail;
import com.hcmute.lms.lmsapi.enums.EnrollmentStatus;
import com.hcmute.lms.lmsapi.enums.SectionStatus;
import com.hcmute.lms.lmsapi.enums.TermStatus;
import com.hcmute.lms.lmsapi.exception.BusinessRuleViolationException;
import com.hcmute.lms.lmsapi.exception.ImportValidationException;
import com.hcmute.lms.lmsapi.exception.ResourceNotFoundException;
import com.hcmute.lms.lmsapi.mapper.impl.EnrollmentMapperImpl;
import com.hcmute.lms.lmsapi.repository.CourseSectionRepository;
import com.hcmute.lms.lmsapi.repository.EnrollmentRepository;
import com.hcmute.lms.lmsapi.repository.StudentRepository;
import com.hcmute.lms.lmsapi.repository.TermRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
        .thenReturn(Optional.of(Student.builder().id(STUDENT_ID).studentRef("SV-1").build()));
    stubOpenTerm();
    when(enrollmentRepository.save(any(Enrollment.class))).thenAnswer(i -> i.getArgument(0));
    when(enrollmentRepository.countBySectionIdAndStatus(SECTION_ID, EnrollmentStatus.ENROLLED))
        .thenReturn(1L);

    EnrollmentResponse response =
        service.updateStatus(ENROLLMENT_ID, request(EnrollmentStatus.WITHDRAWN), "actor");

    assertThat(response.getStatus()).isEqualTo(EnrollmentStatus.WITHDRAWN);
    assertThat(response.getStudentRef()).isEqualTo("SV-1");
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
                Student.builder().id("671dcba5f123456789abc702").studentRef("SV-A").build(),
                Student.builder().id("671dcba5f123456789abc701").studentRef("SV-B").build()));
    when(enrollmentRepository.countBySectionIdAndStatus(SECTION_ID, EnrollmentStatus.ENROLLED))
        .thenReturn(2L);
    when(enrollmentRepository.countBySectionIdAndStatus(SECTION_ID, EnrollmentStatus.WITHDRAWN))
        .thenReturn(1L);

    EnrollmentListResponse response = service.getEnrollments(SECTION_ID, EnrollmentStatus.ENROLLED);

    assertThat(response.getEnrolled()).isEqualTo(2);
    assertThat(response.getWithdrawn()).isEqualTo(1);
    assertThat(response.getEnrollments())
        .extracting(EnrollmentResponse::getStudentRef)
        .containsExactly("SV-A", "SV-B");
  }

  private java.io.InputStream csv(String content) {
    return new java.io.ByteArrayInputStream(
        content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  @Test
  @DisplayName(
      "US-20/AC-1: nhập tệp -> chỉ tạo sinh viên mới, dùng lại sinh viên cũ, tính lại sĩ số")
  void importEnrollments_createsNewAndReusesExisting() {
    CourseSection section = section(SectionStatus.OPEN);
    when(sectionRepository.findById(SECTION_ID)).thenReturn(Optional.of(section));
    stubOpenTerm();
    Student existing = Student.builder().id(STUDENT_ID).studentRef("SV-OLD").build();
    when(studentRepository.findByStudentRefIn(List.of("SV-OLD", "SV-NEW")))
        .thenReturn(List.of(existing));
    when(enrollmentRepository.findBySectionId(SECTION_ID)).thenReturn(List.of());
    when(studentRepository.saveAll(any()))
        .thenAnswer(
            invocation -> {
              List<Student> students = invocation.getArgument(0);
              students.forEach(s -> s.setId("671dcba5f123456789abc799"));
              return students;
            });
    when(enrollmentRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));
    when(enrollmentRepository.countBySectionIdAndStatus(SECTION_ID, EnrollmentStatus.ENROLLED))
        .thenReturn(2L);

    EnrollmentImportResponse response =
        service.importEnrollments(
            SECTION_ID, csv("studentRef,cohort\nSV-OLD,K22\nSV-NEW,K23\n"), "a");

    assertThat(response.getTotalRows()).isEqualTo(2);
    assertThat(response.getNewStudents()).isEqualTo(1);
    assertThat(response.getReusedStudents()).isEqualTo(1);
    assertThat(response.getSectionEnrolledCount()).isEqualTo(2);
    ArgumentCaptor<List<Student>> saved = ArgumentCaptor.captor();
    verify(studentRepository).saveAll(saved.capture());
    assertThat(saved.getValue()).extracting(Student::getStudentRef).containsExactly("SV-NEW");
    assertThat(saved.getValue().get(0).getCohort()).isEqualTo("K23");
  }

  @Test
  @DisplayName(
      "US-20/AC-2: gom đủ mọi lỗi theo dòng (thiếu, sai mẫu, quá dài, trùng, đã ghi danh, đã rút), không ghi gì")
  void importEnrollments_collectsAllRowErrorsAndWritesNothing() {
    when(sectionRepository.findById(SECTION_ID))
        .thenReturn(Optional.of(section(SectionStatus.OPEN)));
    stubOpenTerm();
    Student enrolledStudent = Student.builder().id("s1").studentRef("SV-IN").build();
    Student withdrawnStudent = Student.builder().id("s2").studentRef("SV-OUT").build();
    when(studentRepository.findByStudentRefIn(any()))
        .thenReturn(List.of(enrolledStudent, withdrawnStudent));
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
        "studentRef,cohort\n"
            + "\"\",K22\n" // line 2: thiếu
            + "BAD REF,K22\n" // line 3: sai mẫu
            + "SV-1,"
            + tooLong
            + "\n" // line 4: cohort quá dài
            + "SV-2,K22\n" // line 5: hợp lệ
            + "SV-2,K22\n" // line 6: trùng dòng 5
            + "SV-IN,K22\n" // line 7: đã ghi danh
            + "SV-OUT,K22\n"; // line 8: đã rút

    assertThatThrownBy(() -> service.importEnrollments(SECTION_ID, csv(content), "a"))
        .isInstanceOfSatisfying(
            ImportValidationException.class,
            ex ->
                assertThat(ex.getRowErrors())
                    .extracting(FieldErrorDetail::getField)
                    .containsExactly("line 2", "line 3", "line 4", "line 6", "line 7", "line 8"));

    verify(studentRepository, never()).saveAll(any());
    verify(enrollmentRepository, never()).saveAll(any());
    verify(sectionRepository, never()).save(any());
  }

  @Test
  @DisplayName("US-20: lớp không tồn tại -> 404; lớp đóng băng -> 422 trước khi đọc tệp")
  void importEnrollments_sectionRules() {
    when(sectionRepository.findById(SECTION_ID)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.importEnrollments(SECTION_ID, csv("studentRef\nSV-1\n"), "a"))
        .isInstanceOf(ResourceNotFoundException.class);

    when(sectionRepository.findById(SECTION_ID))
        .thenReturn(Optional.of(section(SectionStatus.LOCKED)));
    assertThatThrownBy(() -> service.importEnrollments(SECTION_ID, csv("not even a csv"), "a"))
        .isInstanceOf(BusinessRuleViolationException.class);
    verify(studentRepository, never()).findByStudentRefIn(any());
  }

  @Test
  @DisplayName("getEnrollments: lớp không tồn tại -> 404")
  void getEnrollments_missingSection() {
    when(sectionRepository.findById(SECTION_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getEnrollments(SECTION_ID, null))
        .isInstanceOf(ResourceNotFoundException.class);
  }
}
