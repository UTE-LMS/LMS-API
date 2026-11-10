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
import com.hcmute.lms.lmsapi.document.Term;
import com.hcmute.lms.lmsapi.dto.ImportBatchCreateRequest;
import com.hcmute.lms.lmsapi.dto.ImportColumnMappingDto;
import com.hcmute.lms.lmsapi.dto.UserPrincipal;
import com.hcmute.lms.lmsapi.enums.AssessmentScope;
import com.hcmute.lms.lmsapi.enums.AssessmentStatus;
import com.hcmute.lms.lmsapi.enums.EmptyCellAs;
import com.hcmute.lms.lmsapi.enums.ImportBatchStatus;
import com.hcmute.lms.lmsapi.enums.ReimportPolicy;
import com.hcmute.lms.lmsapi.enums.TermStatus;
import com.hcmute.lms.lmsapi.exception.AppException;
import com.hcmute.lms.lmsapi.exception.ResourceNotFoundException;
import com.hcmute.lms.lmsapi.mapper.impl.ImportBatchMapperImpl;
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
@DisplayName("F05b: ImportBatchServiceImpl - nhánh khó đạt qua API (US-21, US-22, US-24)")
class ImportBatchServiceImplTest {

  private static final byte[] CSV = "studentRef,Q1\nS1,3\n".getBytes(StandardCharsets.UTF_8);

  @Mock private ImportBatchRepository batchRepository;
  @Mock private ImportRowErrorRepository rowErrorRepository;
  @Mock private ScoreRepository scoreRepository;
  @Mock private CourseSectionRepository sectionRepository;
  @Mock private TermRepository termRepository;
  @Mock private AssessmentRepository assessmentRepository;
  @Mock private EnrollmentRepository enrollmentRepository;
  @Mock private StudentRepository studentRepository;
  @Mock private ImportFileStorage fileStorage;
  @Mock private AuditService auditService;

  private ImportBatchServiceImpl service;

  private final String sectionId = new ObjectId().toHexString();
  private final String assessmentId = new ObjectId().toHexString();
  private final String itemId = new ObjectId().toHexString();
  private final UserPrincipal actor =
      UserPrincipal.builder().id(new ObjectId().toHexString()).username("gv-thu-nghiem").build();

  @BeforeEach
  void setUp() {
    service =
        new ImportBatchServiceImpl(
            batchRepository,
            rowErrorRepository,
            scoreRepository,
            sectionRepository,
            termRepository,
            assessmentRepository,
            enrollmentRepository,
            studentRepository,
            fileStorage,
            new ScoreRowValidator(),
            auditService,
            new ImportBatchMapperImpl());
  }

  private void stubOpenSectionAndAssessment() {
    String termId = new ObjectId().toHexString();
    when(sectionRepository.findById(sectionId))
        .thenReturn(
            Optional.of(CourseSection.builder().id(sectionId).code("SEC").termId(termId).build()));
    when(termRepository.findById(termId))
        .thenReturn(
            Optional.of(Term.builder().id(termId).code("T").status(TermStatus.OPEN).build()));
    when(assessmentRepository.findById(assessmentId))
        .thenReturn(
            Optional.of(
                Assessment.builder()
                    .id(assessmentId)
                    .code("AS")
                    .scope(AssessmentScope.SECTION)
                    .sectionId(sectionId)
                    .status(AssessmentStatus.APPROVED)
                    .items(
                        List.of(
                            AssessmentItem.builder()
                                .id(itemId)
                                .code("Q1")
                                .maxScore(4.0)
                                .externalRef("EXT-1")
                                .build()))
                    .build()));
  }

  private ImportBatchCreateRequest request() {
    return ImportBatchCreateRequest.builder()
        .emptyCellAs(EmptyCellAs.BLANK)
        .reimportPolicy(ReimportPolicy.REJECT_ON_CONFLICT)
        .columnMapping(
            ImportColumnMappingDto.builder()
                .items(List.of(new ImportColumnMappingDto.Item("Q1", "EXT-1")))
                .build())
        .build();
  }

  @Test
  @DisplayName(
      "US-21 (ADR 0013): ghi lô thất bại thì tệp vừa lưu bị xóa và lỗi được ném lại, không để tệp mồ côi")
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
  @DisplayName(
      "US-21: tên tệp người dùng gửi được làm sạch (bỏ đường dẫn, cắt 255 ký tự, rỗng -> upload.csv)")
  void fileNameIsSanitised() {
    stubOpenSectionAndAssessment();
    when(fileStorage.save(anyString(), any())).thenReturn("ref.csv");
    when(batchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    String longName = "x".repeat(300);
    for (String raw :
        new String[] {
          "C:\\Users\\gv\\diem.csv", "../../etc/diem.csv", null, "  ", "dir/", longName
        }) {
      service.createBatch(sectionId, assessmentId, raw, CSV, request(), actor);
    }

    ArgumentCaptor<ImportBatch> saved = ArgumentCaptor.forClass(ImportBatch.class);
    verify(batchRepository, org.mockito.Mockito.times(6)).save(saved.capture());
    assertThat(saved.getAllValues())
        .extracting(ImportBatch::getFileName)
        .containsExactly(
            "diem.csv", "diem.csv", "upload.csv", "upload.csv", "upload.csv", "x".repeat(255));
    assertThat(saved.getValue().getStatus()).isEqualTo(ImportBatchStatus.UPLOADED);
    assertThat(saved.getValue().getUploadedBy()).isEqualTo(actor.getId());
  }

  @Test
  @DisplayName(
      "US-21: thiếu emptyCellAs/reimportPolicy ở tầng service -> 400, không lưu tệp, không ghi lô")
  void serviceGuardsMandatoryChoices() {
    stubOpenSectionAndAssessment();
    ImportBatchCreateRequest noEmptyChoice = request();
    noEmptyChoice.setEmptyCellAs(null);
    ImportBatchCreateRequest noPolicy = request();
    noPolicy.setReimportPolicy(null);

    for (ImportBatchCreateRequest bad : List.of(noEmptyChoice, noPolicy)) {
      assertThatThrownBy(
              () -> service.createBatch(sectionId, assessmentId, "a.csv", CSV, bad, actor))
          .isInstanceOfSatisfying(
              AppException.class, ex -> assertThat(ex.getHttpStatus().value()).isEqualTo(400));
    }
    verify(fileStorage, never()).save(anyString(), any());
    verify(batchRepository, never()).save(any());
  }

  @Test
  @DisplayName(
      "US-22: tệp gốc đã lưu bị đổi (checksum lệch) -> lỗi 500, không ghi lỗi dòng, không đổi trạng thái lô")
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
                    .items(List.of(new ImportColumnMapping.Entry("Q1", itemId)))
                    .build())
            .emptyCellAs(EmptyCellAs.BLANK)
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
    verify(batchRepository, never()).save(any());
    assertThat(batch.getStatus()).isEqualTo(ImportBatchStatus.UPLOADED);
  }

  @Test
  @DisplayName("US-22: lô không tồn tại -> 404 (xem lỗi, xem lô); id sai định dạng -> 400")
  void unknownBatchIsNotFound() {
    String missing = new ObjectId().toHexString();
    when(batchRepository.findById(missing)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getBatch(missing))
        .isInstanceOf(ResourceNotFoundException.class);
    assertThatThrownBy(
            () -> service.getErrors(missing, org.springframework.data.domain.PageRequest.of(0, 5)))
        .isInstanceOf(ResourceNotFoundException.class);
    assertThatThrownBy(() -> service.rollbackBatch(missing, actor))
        .isInstanceOf(ResourceNotFoundException.class);
    assertThatThrownBy(() -> service.getBatch("khong-hop-le"))
        .isInstanceOfSatisfying(
            AppException.class, ex -> assertThat(ex.getHttpStatus().value()).isEqualTo(400));
  }
}
