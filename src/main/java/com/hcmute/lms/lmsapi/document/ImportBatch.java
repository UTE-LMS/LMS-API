package com.hcmute.lms.lmsapi.document;

import com.hcmute.lms.lmsapi.enums.EmptyCellAs;
import com.hcmute.lms.lmsapi.enums.ImportBatchStatus;
import com.hcmute.lms.lmsapi.enums.ImportSourceSystem;
import com.hcmute.lms.lmsapi.enums.ReimportPolicy;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

/** Một lần nhập tệp điểm cho một lớp và một bài đánh giá (§4.20 / F05b, US-21..24). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "import_batches")
@CompoundIndexes({
  @CompoundIndex(
      name = "import_batches_section_assessment_created_idx",
      def = "{'sectionId': 1, 'assessmentId': 1, 'createdAt': -1}"),
  @CompoundIndex(name = "import_batches_checksum_idx", def = "{'fileChecksum': 1}")
})
public class ImportBatch {

  @Id private String id;

  @Field(name = "sectionId", targetType = FieldType.OBJECT_ID)
  private String sectionId;

  @Field(name = "assessmentId", targetType = FieldType.OBJECT_ID)
  private String assessmentId;

  /** Tên tệp do người dùng gửi (chỉ để hiển thị, không dùng làm đường dẫn). */
  @Field("fileName")
  private String fileName;

  /** SHA-256 (hex) của nội dung tệp, để chặn nhập trùng. */
  @Field("fileChecksum")
  private String fileChecksum;

  /** Tên tệp gốc đã lưu do máy chủ đặt (ADR 0013). */
  @Field("fileRef")
  private String fileRef;

  @Field("sourceSystem")
  private ImportSourceSystem sourceSystem;

  @Field("columnMapping")
  private ImportColumnMapping columnMapping;

  @Field("emptyCellAs")
  private EmptyCellAs emptyCellAs;

  @Field("reimportPolicy")
  private ReimportPolicy reimportPolicy;

  @Builder.Default
  @Field("status")
  private ImportBatchStatus status = ImportBatchStatus.UPLOADED;

  @Field("stats")
  private ImportStats stats;

  @Field(name = "uploadedBy", targetType = FieldType.OBJECT_ID)
  private String uploadedBy;

  @Field(name = "confirmedBy", targetType = FieldType.OBJECT_ID)
  private String confirmedBy;

  @Field("confirmedAt")
  private Instant confirmedAt;

  @Field("createdAt")
  private Instant createdAt;

  /**
   * Khóa lạc quan: hai người cùng xác nhận một lô thì chỉ một người ghi thành công, người còn lại
   * nhận xung đột (HTTP 409).
   */
  @Version
  @Field("revision")
  private Long revision;
}
