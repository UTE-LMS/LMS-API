package com.hcmute.lms.lmsapi.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hcmute.lms.lmsapi.document.Faculty;
import com.hcmute.lms.lmsapi.dto.AssessmentScoreTemplate;
import com.hcmute.lms.lmsapi.dto.CsvImportResultResponse;
import com.hcmute.lms.lmsapi.mapper.UserMapper;
import com.hcmute.lms.lmsapi.repository.FacultyRepository;
import com.hcmute.lms.lmsapi.repository.ProgramRepository;
import com.hcmute.lms.lmsapi.repository.ProgramVersionRepository;
import com.hcmute.lms.lmsapi.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
@DisplayName("FacultyServiceImpl Import Unit Tests")
class FacultyServiceImplTest {

  @Mock private FacultyRepository facultyRepository;
  @Mock private UserRepository userRepository;
  @Mock private ProgramRepository programRepository;
  @Mock private ProgramVersionRepository programVersionRepository;
  @Mock private UserMapper userMapper;

  private FacultyServiceImpl facultyService;

  @BeforeEach
  void setUp() {
    facultyService =
        new FacultyServiceImpl(
            facultyRepository,
            userRepository,
            programRepository,
            programVersionRepository,
            userMapper);
  }

  @Test
  @DisplayName("generateTemplateCsv should omit isActive column")
  void shouldGenerateTemplateWithoutIsActiveColumn() {
    AssessmentScoreTemplate template = facultyService.generateTemplateCsv();

    assertThat(template.fileName()).isEqualTo("faculties-template.csv");
    String content = new String(template.content(), StandardCharsets.UTF_8);
    assertThat(content).startsWith("\uFEFFcode,name,description\r\n");
    assertThat(content).doesNotContain("isActive");
  }

  @Test
  @DisplayName("importFaculties without isActive column should always save active faculties")
  void shouldImportFacultiesWithDefaultActiveTrue() {
    List<Faculty> saved =
        importValidFaculties(
            "code,name,description\n"
                + "fit,Khoa Công nghệ Thông tin,Đào tạo CNTT\n"
                + "fe,Khoa Kinh tế,\n");

    assertThat(saved).allSatisfy(f -> assertThat(f.isActive()).isTrue());
    assertThat(saved.get(0).getCode()).isEqualTo("FIT");
    assertThat(saved.get(1).getDescription()).isNull();
  }

  @Test
  @DisplayName("importFaculties should ignore legacy isActive column and always force active=true")
  void shouldIgnoreLegacyIsActiveColumnAndForceActive() {
    List<Faculty> saved =
        importValidFaculties(
            "code,name,description,isActive\n"
                + "FME,Khoa Cơ khí,Mô tả FME,false\n"
                + "FFL,Khoa Ngoại ngữ,Mô tả FFL,not_a_bool\n");

    assertThat(saved).extracting(Faculty::isActive).containsOnly(true);
  }

  private List<Faculty> importValidFaculties(String csv) {
    when(facultyRepository.existsByCode(anyString())).thenReturn(false);
    when(facultyRepository.existsByName(anyString())).thenReturn(false);
    when(facultyRepository.save(any(Faculty.class))).thenAnswer(inv -> inv.getArgument(0));

    MockMultipartFile file =
        new MockMultipartFile(
            "file", "faculties.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
    CsvImportResultResponse result = facultyService.importFaculties(file);

    assertThat(result.getTotalRows()).isEqualTo(2);
    assertThat(result.getSuccessCount()).isEqualTo(2);
    assertThat(result.getFailureCount()).isZero();

    ArgumentCaptor<Faculty> captor = ArgumentCaptor.forClass(Faculty.class);
    verify(facultyRepository, times(2)).save(captor.capture());
    return captor.getAllValues();
  }

  @Test
  @DisplayName("importFaculties should collect row errors for missing and duplicate fields")
  void shouldReportValidationAndDuplicateErrors() {
    when(facultyRepository.existsByCode("EXISTING")).thenReturn(true);
    when(facultyRepository.existsByCode("NEW_CODE")).thenReturn(false);
    when(facultyRepository.existsByName("Tên đã có")).thenReturn(true);
    when(facultyRepository.existsByCode("OK")).thenReturn(false);
    when(facultyRepository.existsByName("Khoa Hợp Lệ")).thenReturn(false);
    when(facultyRepository.save(any(Faculty.class))).thenAnswer(inv -> inv.getArgument(0));

    String csv =
        "code,name,description\n"
            + ",Khoa thiếu mã,Desc\n"
            + "NO_NAME,,Desc\n"
            + "EXISTING,Khoa trùng mã DB,Desc\n"
            + "NEW_CODE,Tên đã có,Desc\n"
            + "OK,Khoa Hợp Lệ,Desc\n"
            + "OK,Khoa Trùng File,Desc\n";
    MockMultipartFile file =
        new MockMultipartFile(
            "file", "invalid-faculties.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

    CsvImportResultResponse result = facultyService.importFaculties(file);

    assertThat(result.getTotalRows()).isEqualTo(6);
    assertThat(result.getSuccessCount()).isEqualTo(1);
    assertThat(result.getFailureCount()).isEqualTo(5);
    assertThat(result.getErrors()).hasSize(5);
  }
}
