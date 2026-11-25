package com.hcmute.lms.lmsapi.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hcmute.lms.lmsapi.document.Student;
import com.hcmute.lms.lmsapi.dto.AssessmentScoreTemplate;
import com.hcmute.lms.lmsapi.dto.CsvImportResultResponse;
import com.hcmute.lms.lmsapi.dto.PageResponse;
import com.hcmute.lms.lmsapi.dto.StudentBatchStatusUpdateRequest;
import com.hcmute.lms.lmsapi.dto.StudentBatchStatusUpdateResponse;
import com.hcmute.lms.lmsapi.dto.StudentResponse;
import com.hcmute.lms.lmsapi.dto.StudentStatusUpdateRequest;
import com.hcmute.lms.lmsapi.enums.StudentStatus;
import com.hcmute.lms.lmsapi.exception.AppException;
import com.hcmute.lms.lmsapi.exception.BusinessRuleViolationException;
import com.hcmute.lms.lmsapi.exception.ResourceNotFoundException;
import com.hcmute.lms.lmsapi.repository.StudentRepository;
import com.hcmute.lms.lmsapi.util.XlsxCsvConverter;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
@DisplayName("StudentServiceImpl: Quản lý, import sinh viên và cập nhật trạng thái hàng loạt")
class StudentServiceImplTest {

  private static final String ID_1 = "671dcba5f123456789abc101";
  private static final String ID_2 = "671dcba5f123456789abc102";

  @Mock private StudentRepository studentRepository;

  private StudentServiceImpl studentService;

  @BeforeEach
  void setUp() {
    studentService = new StudentServiceImpl(studentRepository);
    lenient().when(studentRepository.save(any(Student.class))).thenAnswer(i -> i.getArgument(0));
  }

  private MockMultipartFile csvFile(String content) {
    return new MockMultipartFile(
        "file", "students.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
  }

  private MockMultipartFile xlsxFile(String csvContent) {
    byte[] xlsx = XlsxCsvConverter.fromCsv(csvContent.getBytes(StandardCharsets.UTF_8));
    return new MockMultipartFile(
        "file",
        "students.xlsx",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        xlsx);
  }

  private Student student(String id, String code, StudentStatus status) {
    return Student.builder()
        .id(id)
        .studentCode(code)
        .fullName("SV " + code)
        .cohort("K23")
        .status(status)
        .build();
  }

  @Test
  @DisplayName("getStudents: phân trang và ánh xạ StudentResponse")
  void getStudents_ReturnsPageResponse() {
    PageRequest pageable = PageRequest.of(0, 10);
    Student s1 = student(ID_1, "23110001", StudentStatus.ACTIVE);
    when(studentRepository.searchStudents("2311", "K23", StudentStatus.ACTIVE, pageable))
        .thenReturn(new PageImpl<>(List.of(s1), pageable, 1));

    PageResponse<StudentResponse> response =
        studentService.getStudents(0, 10, "2311", "K23", StudentStatus.ACTIVE);

    assertThat(response.getTotalElements()).isEqualTo(1);
    assertThat(response.getContent()).hasSize(1);
    assertThat(response.getContent().get(0).getStudentCode()).isEqualTo("23110001");
    assertThat(response.getContent().get(0).getStatus()).isEqualTo(StudentStatus.ACTIVE);
  }

  @Test
  @DisplayName("updateStudentStatus: đổi trạng thái, no-op khi trùng, báo lỗi 404/400")
  void updateStudentStatus_HandlesUpdateNoOpAndNotFound() {
    Student active = student(ID_1, "23110001", StudentStatus.ACTIVE);
    when(studentRepository.findById(ID_1)).thenReturn(Optional.of(active));

    StudentResponse updated =
        studentService.updateStudentStatus(
            ID_1, StudentStatusUpdateRequest.builder().status(StudentStatus.SUSPENDED).build());
    assertThat(updated.getStatus()).isEqualTo(StudentStatus.SUSPENDED);
    verify(studentRepository, times(1)).save(active);

    StudentResponse noOp =
        studentService.updateStudentStatus(
            ID_1, StudentStatusUpdateRequest.builder().status(StudentStatus.SUSPENDED).build());
    assertThat(noOp.getStatus()).isEqualTo(StudentStatus.SUSPENDED);
    verify(studentRepository, times(1)).save(active);

    when(studentRepository.findById(ID_2)).thenReturn(Optional.empty());
    assertThatThrownBy(
            () ->
                studentService.updateStudentStatus(
                    ID_2,
                    StudentStatusUpdateRequest.builder().status(StudentStatus.ACTIVE).build()))
        .isInstanceOf(ResourceNotFoundException.class);
    assertThatThrownBy(
            () ->
                studentService.updateStudentStatus(
                    "bad-id",
                    StudentStatusUpdateRequest.builder().status(StudentStatus.ACTIVE).build()))
        .isInstanceOf(AppException.class);
  }

  @Test
  @DisplayName(
      "updateStudentsStatusBatch: khử trùng ID, chỉ ghi bản ghi đổi trạng thái, thiếu ID -> 404 không ghi gì")
  void updateStudentsStatusBatch_UpdatesAndValidatesAllIds() {
    Student s1 = student(ID_1, "23110001", StudentStatus.ACTIVE);
    Student s2 = student(ID_2, "23110002", StudentStatus.GRADUATED);
    when(studentRepository.findAllById(any())).thenReturn(List.of(s1, s2));
    when(studentRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));

    StudentBatchStatusUpdateResponse result =
        studentService.updateStudentsStatusBatch(
            StudentBatchStatusUpdateRequest.builder()
                .studentIds(List.of(ID_2, ID_1, ID_2))
                .status(StudentStatus.GRADUATED)
                .build());

    assertThat(result.getRequestedCount()).isEqualTo(2);
    assertThat(result.getUpdatedCount()).isEqualTo(1);
    assertThat(result.getStatus()).isEqualTo(StudentStatus.GRADUATED);
    assertThat(result.getStudents())
        .extracting(StudentResponse::getStudentCode)
        .containsExactly("23110001", "23110002");

    when(studentRepository.findAllById(any())).thenReturn(List.of(s1));
    assertThatThrownBy(
            () ->
                studentService.updateStudentsStatusBatch(
                    StudentBatchStatusUpdateRequest.builder()
                        .studentIds(List.of(ID_1, ID_2))
                        .status(StudentStatus.DROPPED_OUT)
                        .build()))
        .isInstanceOf(ResourceNotFoundException.class);
    verify(studentRepository, times(1)).saveAll(any());
  }

  @Test
  @DisplayName("generateTemplateCsv: đúng tên file, đủ cột mẫu và không chứa cột status")
  void generateTemplateCsv_ReturnsExpectedColumns() {
    AssessmentScoreTemplate template = studentService.generateTemplateCsv();
    String text = new String(template.content(), StandardCharsets.UTF_8);

    assertThat(template.fileName()).isEqualTo("students-template.csv");
    assertThat(text).contains("studentCode,fullName,cohort");
    assertThat(text).doesNotContain("status");
  }

  @Test
  @DisplayName("importStudents: lưu sinh viên hợp lệ với status=ACTIVE và cắt khoảng trắng thừa")
  void importStudents_ValidCsv_SavesActiveStudents() {
    String csv =
        "studentCode,fullName,cohort\n"
            + " 23110001 , Nguyễn Văn An , K23 \n"
            + "23110002, Trần Thị Bình , K24\n";

    CsvImportResultResponse result = studentService.importStudents(csvFile(csv));

    assertThat(result.getTotalRows()).isEqualTo(2);
    assertThat(result.getSuccessCount()).isEqualTo(2);
    assertThat(result.getFailureCount()).isZero();
    assertThat(result.getErrors()).isEmpty();

    ArgumentCaptor<Student> captor = ArgumentCaptor.forClass(Student.class);
    verify(studentRepository, times(2)).save(captor.capture());
    Student first = captor.getAllValues().get(0);
    Student second = captor.getAllValues().get(1);

    assertThat(first.getStudentCode()).isEqualTo("23110001");
    assertThat(first.getFullName()).isEqualTo("Nguyễn Văn An");
    assertThat(first.getCohort()).isEqualTo("K23");
    assertThat(first.getStatus()).isEqualTo(StudentStatus.ACTIVE);

    assertThat(second.getStudentCode()).isEqualTo("23110002");
    assertThat(second.getFullName()).isEqualTo("Trần Thị Bình");
    assertThat(second.getCohort()).isEqualTo("K24");
    assertThat(second.getStatus()).isEqualTo(StudentStatus.ACTIVE);
  }

  @Test
  @DisplayName("importStudents: hỗ trợ tệp Excel (.xlsx)")
  void importStudents_ValidXlsx_SavesStudents() {
    String csv = "studentCode,fullName,cohort\n23110009,Lê Thị Cúc,K23\n";

    CsvImportResultResponse result = studentService.importStudents(xlsxFile(csv));

    assertThat(result.getTotalRows()).isEqualTo(1);
    assertThat(result.getSuccessCount()).isEqualTo(1);
    assertThat(result.getFailureCount()).isZero();
  }

  @Test
  @DisplayName(
      "importStudents: best-effort gom lỗi từng dòng (thiếu mã/tên/khóa, sai mẫu, quá dài, trùng file/DB)")
  void importStudents_BestEffortCollectsRowErrors() {
    when(studentRepository.existsByStudentCode("23110002")).thenReturn(true);
    when(studentRepository.existsByStudentCode("23110001")).thenReturn(false);
    when(studentRepository.existsByStudentCode("23110003")).thenReturn(false);
    when(studentRepository.save(any(Student.class)))
        .thenAnswer(i -> i.getArgument(0))
        .thenThrow(new DuplicateKeyException("dup"));

    String csv =
        "studentCode,fullName,cohort\n"
            + ",Không có mã,K23\n"
            + "BAD CODE,Sai ký tự,K23\n"
            + "23110001,,K23\n"
            + "23110001,Thiếu khóa,\n"
            + "23110001,"
            + "A".repeat(151)
            + ",K23\n"
            + "23110001,Khóa quá dài,"
            + "K".repeat(51)
            + "\n"
            + "23110001,Hợp lệ,K23\n"
            + "23110001,Trùng trong file,K23\n"
            + "23110002,Đã có trong DB,K23\n"
            + "23110003,Lỗi trùng đồng thời,K23\n";

    CsvImportResultResponse result = studentService.importStudents(csvFile(csv));

    assertThat(result.getTotalRows()).isEqualTo(10);
    assertThat(result.getSuccessCount()).isEqualTo(1);
    assertThat(result.getFailureCount()).isEqualTo(9);
    assertThat(result.getErrors())
        .extracting(CsvImportResultResponse.RowError::getRow)
        .containsExactly(2, 3, 4, 5, 6, 7, 9, 10, 11);
  }

  @Test
  @DisplayName(
      "importStudents: thiếu cột bắt buộc (studentCode/fullName/cohort) hoặc chỉ có header -> ném lỗi")
  void importStudents_MissingRequiredColumnOrEmptyData_Throws() {
    assertThatThrownBy(() -> studentService.importStudents(csvFile("fullName,cohort\nAn,K23\n")))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessageContaining("studentCode");
    assertThatThrownBy(
            () -> studentService.importStudents(csvFile("studentCode,cohort\n23110001,K23\n")))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessageContaining("fullName");
    assertThatThrownBy(
            () -> studentService.importStudents(csvFile("studentCode,fullName\n23110001,An\n")))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessageContaining("cohort");

    assertThatThrownBy(
            () -> studentService.importStudents(csvFile("studentCode,fullName,cohort\n")))
        .isInstanceOf(BusinessRuleViolationException.class);
  }

  @Test
  @DisplayName(
      "generateStatusTemplateCsv & importStudentStatuses: đổi trạng thái CSV/XLSX và báo lỗi từng dòng")
  void statusImport_ValidatesAndUpdatesBestEffort() {
    AssessmentScoreTemplate template = studentService.generateStatusTemplateCsv();
    assertThat(template.fileName()).isEqualTo("students-status-template.csv");
    assertThat(new String(template.content(), StandardCharsets.UTF_8))
        .contains("studentCode,status");

    Student s1 = student(ID_1, "23110001", StudentStatus.ACTIVE);
    when(studentRepository.findByStudentCode("23110001")).thenReturn(Optional.of(s1));
    when(studentRepository.findByStudentCode("23110404")).thenReturn(Optional.empty());

    String csv =
        "studentCode,status\n"
            + "23110001,graduated\n"
            + ",SUSPENDED\n"
            + "BAD CODE,SUSPENDED\n"
            + "23110001,UNKNOWN_STATUS\n"
            + "23110001,ACTIVE\n"
            + "23110404,DROPPED_OUT\n";

    CsvImportResultResponse result = studentService.importStudentStatuses(csvFile(csv));

    assertThat(result.getTotalRows()).isEqualTo(6);
    assertThat(result.getSuccessCount()).isEqualTo(1);
    assertThat(result.getFailureCount()).isEqualTo(5);
    assertThat(result.getErrors())
        .extracting(CsvImportResultResponse.RowError::getRow)
        .containsExactly(3, 4, 5, 6, 7);
    assertThat(s1.getStatus()).isEqualTo(StudentStatus.GRADUATED);

    CsvImportResultResponse xlsxResult =
        studentService.importStudentStatuses(xlsxFile("studentCode,status\n23110001,SUSPENDED\n"));
    assertThat(xlsxResult.getSuccessCount()).isEqualTo(1);
    assertThat(s1.getStatus()).isEqualTo(StudentStatus.SUSPENDED);

    assertThatThrownBy(
            () -> studentService.importStudentStatuses(csvFile("studentCode\n23110001\n")))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessageContaining("status");
    verify(studentRepository, never()).saveAll(any());
  }
}
