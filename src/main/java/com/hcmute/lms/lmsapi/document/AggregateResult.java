package com.hcmute.lms.lmsapi.document;

import com.hcmute.lms.lmsapi.enums.AggregateScopeType;
import com.hcmute.lms.lmsapi.enums.ResultLevel;
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

/** Kết quả tổng hợp theo bài đánh giá / lớp / khóa / CTĐT (collection aggregate_results, F06). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "aggregate_results")
@CompoundIndexes({
  @CompoundIndex(
      name = "uniq_aggregate_scope",
      def =
          "{'runId': 1, 'level': 1, 'subjectId': 1, 'sectionId': 1, 'assessmentId': 1,"
              + " 'scope.type': 1, 'scope.refId': 1, 'scope.cohort': 1, 'scope.termId': 1}",
      unique = true),
  @CompoundIndex(
      name = "agg_level_subject_term_idx",
      def = "{'level': 1, 'subjectId': 1, 'scope.termId': 1}")
})
public class AggregateResult {

  @Id private String id;

  @Field(name = "runId", targetType = FieldType.OBJECT_ID)
  private String runId;

  @Field("level")
  private ResultLevel level;

  @Field(name = "subjectId", targetType = FieldType.OBJECT_ID)
  private String subjectId;

  @Field(name = "sectionId", targetType = FieldType.OBJECT_ID)
  private String sectionId;

  @Field(name = "assessmentId", targetType = FieldType.OBJECT_ID)
  private String assessmentId;

  @Field("scope")
  private Scope scope;

  @Field("counts")
  private Counts counts;

  @Field("mean")
  private Double mean;

  @Field("passRate")
  private Double passRate;

  @Field("passThreshold")
  private Double passThreshold;

  @Field("dataCompleteness")
  private Double dataCompleteness;

  @Field(name = "programVersionId", targetType = FieldType.OBJECT_ID)
  private String programVersionId;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Scope {
    @Field("type")
    private AggregateScopeType type;

    @Field(name = "refId", targetType = FieldType.OBJECT_ID)
    private String refId;

    @Field("cohort")
    private String cohort;

    @Field(name = "termId", targetType = FieldType.OBJECT_ID)
    private String termId;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Counts {
    @Field("enrolled")
    private Integer enrolled;

    @Field("passed")
    private Integer passed;

    @Field("failed")
    private Integer failed;
  }
}
