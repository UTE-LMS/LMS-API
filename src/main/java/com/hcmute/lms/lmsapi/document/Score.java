package com.hcmute.lms.lmsapi.document;

import com.hcmute.lms.lmsapi.enums.ScoreStatus;
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
 * Điểm của một sinh viên ở một câu hỏi/tiêu chí (§4.22). Thiếu dữ liệu không mặc định 0: chỉ BLANK
 * có rawScore = 0; MISSING (và ABSENT, NOT_GRADED, EXEMPT) có rawScore = null (null không được ghi
 * xuống MongoDB, validator chấp nhận trường vắng mặt).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "scores")
@CompoundIndex(
    name = "scores_section_assessment_item_student_unique_idx",
    def = "{'sectionId': 1, 'assessmentId': 1, 'itemId': 1, 'studentId': 1}",
    unique = true)
public class Score {

  @Id private String id;

  @Field(name = "sectionId", targetType = FieldType.OBJECT_ID)
  private String sectionId;

  @Field(name = "assessmentId", targetType = FieldType.OBJECT_ID)
  private String assessmentId;

  @Field(name = "itemId", targetType = FieldType.OBJECT_ID)
  private String itemId;

  @Field(name = "studentId", targetType = FieldType.OBJECT_ID)
  private String studentId;

  @Field("status")
  private ScoreStatus status;

  @Field("rawScore")
  private Double rawScore;

  /** Điểm tối đa của câu tại thời điểm nhập (snapshot từ assessments.items[].maxScore). */
  @Field("maxScore")
  private Double maxScore;

  @Field("rubricLevel")
  private Integer rubricLevel;

  @Field(name = "importBatchId", targetType = FieldType.OBJECT_ID)
  private String importBatchId;

  /** Số dòng trong tệp gốc (dòng tiêu đề là dòng 1). */
  @Field("sourceRow")
  private Integer sourceRow;

  /** Số lần sửa, bắt đầu từ 1 (khóa lạc quan cho US-25). */
  @Field("revision")
  private Integer revision;

  @Field(name = "updatedBy", targetType = FieldType.OBJECT_ID)
  private String updatedBy;

  @Field("updatedAt")
  private Instant updatedAt;
}
