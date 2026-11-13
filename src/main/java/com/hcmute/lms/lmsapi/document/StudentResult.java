package com.hcmute.lms.lmsapi.document;

import com.hcmute.lms.lmsapi.enums.ResultInputSourceType;
import com.hcmute.lms.lmsapi.enums.ResultLevel;
import com.hcmute.lms.lmsapi.enums.ResultStatus;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

/** Kết quả đo lường 4 cấp độ của từng sinh viên (collection student_results, F06). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "student_results")
@CompoundIndexes({
  @CompoundIndex(
      name = "uniq_student_result",
      def =
          "{'runId': 1, 'level': 1, 'subjectId': 1, 'sectionId': 1, 'assessmentId': 1,"
              + " 'studentId': 1}",
      unique = true),
  @CompoundIndex(
      name = "student_level_subject_term_idx",
      def = "{'studentId': 1, 'level': 1, 'subjectId': 1, 'termId': 1}")
})
public class StudentResult {

  @Id private String id;

  @Field(name = "runId", targetType = FieldType.OBJECT_ID)
  private String runId;

  @Field("level")
  private ResultLevel level;

  /** ID của CLO (với ASSESSMENT_CLO / CLO), PI (với PI), hoặc PLO (với PLO). */
  @Field(name = "subjectId", targetType = FieldType.OBJECT_ID)
  private String subjectId;

  @Field(name = "sectionId", targetType = FieldType.OBJECT_ID)
  private String sectionId;

  /** Khác null khi level = ASSESSMENT_CLO; bằng null khi level thuộc CLO | PI | PLO. */
  @Field(name = "assessmentId", targetType = FieldType.OBJECT_ID)
  private String assessmentId;

  @Field(name = "studentId", targetType = FieldType.OBJECT_ID)
  private String studentId;

  @Field(name = "termId", targetType = FieldType.OBJECT_ID)
  private String termId;

  @Field("status")
  private ResultStatus status;

  @Field("value")
  private Double value;

  @Field("passThreshold")
  private Double passThreshold;

  @Field("passed")
  private Boolean passed;

  @Field("completeness")
  private Completeness completeness;

  @Builder.Default
  @Field("inputs")
  private List<ResultInput> inputs = new ArrayList<>();

  @Field(name = "programVersionId", targetType = FieldType.OBJECT_ID)
  private String programVersionId;

  @Field(name = "syllabusId", targetType = FieldType.OBJECT_ID)
  private String syllabusId;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Completeness {
    @Field("requiredCount")
    private Integer requiredCount;

    @Field("availableCount")
    private Integer availableCount;

    @Field("ratio")
    private Double ratio;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ResultInput {
    @Field("sourceType")
    private ResultInputSourceType sourceType;

    @Field(name = "refId", targetType = FieldType.OBJECT_ID)
    private String refId;

    @Field("normalizedValue")
    private Double normalizedValue;

    @Field("weight")
    private Double weight;
  }
}
