package com.hcmute.lms.lmsapi.document;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

/**
 * Điểm của một sinh viên ở một câu hỏi gắn với CLO (ADR 0014, DB Schema v0.7). Vắng thi hoặc bỏ
 * trống câu hỏi lưu trực tiếp rawScore = 0.0 (đã loại bỏ status và rubricLevel).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "scores")
@CompoundIndex(
    name = "scores_section_assessment_item_clo_student_unique_idx",
    def = "{'sectionId': 1, 'assessmentId': 1, 'itemId': 1, 'cloId': 1, 'studentId': 1}",
    unique = true)
public class Score {

  @Id private String id;

  @Field(name = "sectionId", targetType = FieldType.OBJECT_ID)
  private String sectionId;

  @Field(name = "assessmentId", targetType = FieldType.OBJECT_ID)
  private String assessmentId;

  @Field(name = "itemId", targetType = FieldType.OBJECT_ID)
  private String itemId;

  @Field(name = "cloId", targetType = FieldType.OBJECT_ID)
  private String cloId;

  @Field(name = "studentId", targetType = FieldType.OBJECT_ID)
  private String studentId;

  @Field("rawScore")
  private Double rawScore;

  /**
   * Điểm tối đa của câu đối với CLO này tại thời điểm nhập (snapshot từ
   * outcomeMappings[].maxScore).
   */
  @Field("maxScore")
  private Double maxScore;

  @Field(name = "importBatchId", targetType = FieldType.OBJECT_ID)
  private String importBatchId;

  @Field("sourceRow")
  private Integer sourceRow;

  @Field(name = "updatedBy", targetType = FieldType.OBJECT_ID)
  private String updatedBy;

  @Field("updatedAt")
  private Instant updatedAt;
}
