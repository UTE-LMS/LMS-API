package com.hcmute.lms.lmsapi.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hcmute.lms.lmsapi.document.Assessment;
import com.hcmute.lms.lmsapi.document.AssessmentItem;
import com.hcmute.lms.lmsapi.document.CourseSection;
import com.hcmute.lms.lmsapi.document.ImportBatch;
import com.hcmute.lms.lmsapi.document.ImportColumnMapping;
import com.hcmute.lms.lmsapi.document.OutcomeMapping;
import com.hcmute.lms.lmsapi.document.Term;
import com.hcmute.lms.lmsapi.dto.ImportBatchCreateRequest;
import com.hcmute.lms.lmsapi.dto.ImportColumnMappingDto;
import com.hcmute.lms.lmsapi.dto.UserPrincipal;
import com.hcmute.lms.lmsapi.enums.AssessmentScope;
import com.hcmute.lms.lmsapi.enums.AssessmentStatus;
import com.hcmute.lms.lmsapi.enums.ImportBatchStatus;
import com.hcmute.lms.lmsapi.enums.ReimportPolicy;
import com.hcmute.lms.lmsapi.enums.SectionStatus;
import com.hcmute.lms.lmsapi.enums.TermStatus;
import com.hcmute.lms.lmsapi.exception.AppException;
import com.hcmute.lms.lmsapi.mapper.ImportBatchMapper;
import com.hcmute.lms.lmsapi.repository.AssessmentRepository;
import com.hcmute.lms.lmsapi.repository.CourseSectionRepository;
import com.hcmute.lms.lmsapi.repository.EnrollmentRepository;
import com.hcmute.lms.lmsapi.repository.ImportBatchRepository;
import com.hcmute.lms.lmsapi.repository.ImportRowErrorRepository;
import com.hcmute.lms.lmsapi.repository.ScoreRepository;
import com.hcmute.lms.lmsapi.repository.StudentRepository;
import com.hcmute.lms.lmsapi.repository.TermRepository;
import com.hcmute.lms.lmsapi.service.AuditService;
import com.hcmute.lms.lmsapi.service.ImportFileStorage;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("F05b: ImportBatchServiceImpl unit tests")
class ImportBatchServiceImplTest {

  private static final byte[] CSV = "studentRef,Q1\nS1,4\n".getBytes(StandardCharsets.UTF_8);

  @Mock private CourseSectionRepository sectionRepository;
  @Mock private AssessmentRepository assessmentRepository;
  @Mock private TermRepository termRepository;
  @Mock private EnrollmentRepository enrollmentRepository;
  @Mock private StudentRepository studentRepository;
  @Mock private ImportBatchRepository batchRepository;
  @Mock private ImportRowErrorRepository rowErrorRepository;
  @Mock private ScoreRepository scoreRepository;
  @Mock private ImportFileStorage fileStorage;
  @Mock private ScoreRowValidator rowValidator;
  @Mock private ImportBatchMapper batchMapper;
  @Mock private AuditService auditService;

  private ImportBatchServiceImpl service;
  private String sectionId;
  private String assessmentId;
  private String termId;
  private String itemId;
  private String cloId;
  private UserPrincipal actor;

  @BeforeEach
  void setUp() {
    service =
        new ImportBatchServiceImpl(
            sectionRepository,
            assessmentRepository,
            termRepository,
            enrollmentRepository,
            studentRepository,
            batchRepository,
            rowErrorRepository,
            scoreRepository,
            fileStorage,
            rowValidator,
            batchMapper,
            auditService);
    sectionId = new ObjectId().toHexString();
    assessmentId = new ObjectId().toHexString();
    termId = new ObjectId().toHexString();
    itemId = new ObjectId().toHexString();
    cloId = new ObjectId().toHexString();
    actor =
        UserPrincipal.builder()
            .id(new ObjectId().toHexString())
            .username("giangvien")
            .build();
  }

  private void stubOpenSectionAndAssessment() {
    when(sectionRepository.findById(sectionId))
        .thenReturn(
            Optional.of(
                CourseSection.builder()
                    .id(sectionId)
                    .code("CS101-01")
                    .termId(termId)
                    .status(SectionStatus.OPEN)
                    .build()));
    when(termRepository.findById(termId))
        .thenReturn(
            Optional.of(Term.builder().id(termId).code("HK1").status(TermStatus.OPEN).build()));
    when(assessmentRepository.findById(assessmentId))
        .thenReturn(
            Optional.of(
                Assessment.builder()
                    .id(assessmentId)
                    .sectionId(sectionId)
                    .code("GK")
                    .scope(AssessmentScope.SECTION)
                    .status(AssessmentStatus.LOCKED)
                    .items(
                        List.of(
                            AssessmentItem.builder()
                                .id(itemId)
                                .code("Q1")
                                .outcomeMappings(
                                    List.of(
                                        OutcomeMapping.builder()
                                            .cloId(cloId)
                                            .maxScore(4.0)
                                            .build()))
                                .build()))
                    .build()));
  }

  private ImportBatchCreateRequest request() {
    return ImportBatchCreateRequest.builder()
        .reimportPolicy(ReimportPolicy.REJECT_ON_CONFLICT)
        .columnMapping(
            ImportColumnMappingDto.builder()
                .studentColumn("studentRef")
                .items(
                    List.of(
                        ImportColumnMappingDto.Entry.builder()
                            .column("Q1")
                            .itemId(itemId)
                            .cloId(cloId)
                            .build()))
                .build())
        .build();
  }

  @Test
  @DisplayName("US-21 (ADR 0013): ghi lô thất bại thì tệp vừa lưu bị xóa và lỗi được ném lại")
  void createBatchDeletesStoredFileWhenSavingTheBatchFails() {
    stubOpenSectionAndAssessment();
    when(fileStorage.save(anyString(), any())).thenReturn("ref.csv");
    when(batchRepository.save(any())).thenThrow(new IllegalStateException("DỮ LIỆU THỬ NGHIỆM"));

    assertThatThrownBy(
            () -> service.createBatch(sectionId, assessmentId, "a.csv", CSV, request(), actor))
        .isInstanceOf(IllegalStateException.class);

    verify(fileStorage).delete("ref.csv");
  }

  @Test
  @DisplayName("US-21: tên tệp người dùng gửi được làm sạch")
  void fileNameIsSanitised() {
    stubOpenSectionAndAssessment();
    when(fileStorage.save(anyString(), any())).thenReturn("ref.csv");
    when(batchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    for (String raw :
        new String[] {
          "C:\\Users\\gv\\diem.csv", "../../etc/diem.csv", null, "  ", "dir/"
        }) {
      service.createBatch(sectionId, assessmentId, raw, CSV, request(), actor);
    }

    ArgumentCaptor<ImportBatch> saved = ArgumentCaptor.forClass(ImportBatch.class);
    verify(batchRepository, org.mockito.Mockito.times(5)).save(saved.capture());
    assertThat(saved.getAllValues())
        .extracting(ImportBatch::getFileName)
        .containsExactly("diem.csv", "diem.csv", "scores.csv", "scores.csv", "scores.csv");
    assertThat(saved.getValue().getStatus()).isEqualTo(ImportBatchStatus.UPLOADED);
    assertThat(saved.getValue().getUploadedBy()).isEqualTo(actor.getId());
  }

  @Test
  @DisplayName("US-22: tệp gốc đã lưu bị đổi (checksum lệch) -> lỗi 500")
  void validateDetectsTamperedStoredFile() {
    stubOpenSectionAndAssessment();
    ImportBatch batch =
        ImportBatch.builder()
            .id(new ObjectId().toHexString())
            .sectionId(sectionId)
            .assessmentId(assessmentId)
            .fileRef("ref.csv")
            .fileChecksum("0".repeat(64))
            .status(ImportBatchStatus.UPLOADED)
            .columnMapping(
                ImportColumnMapping.builder()
                    .studentColumn("studentRef")
                    .items(
                        List.of(
                            ImportColumnMapping.Entry.builder()
                                .column("Q1")
                                .itemId(itemId)
                                .cloId(cloId)
                                .build()))
                    .build())
            .build();
    when(batchRepository.findById(batch.getId())).thenReturn(Optional.of(batch));
    when(fileStorage.load("ref.csv")).thenReturn(CSV);

    assertThatThrownBy(() -> service.validateBatch(batch.getId(), actor))
        .isInstanceOfSatisfying(
            AppException.class,
            ex -> {
              assertThat(ex.getHttpStatus().value()).isEqualTo(500);
              assertThat(ex.getMessage()).contains("checksum");
            });

    verify(rowErrorRepository, never()).deleteByBatchId(anyString());
  }
}
