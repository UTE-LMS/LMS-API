package com.hcmute.lms.lmsapi.document;

import com.hcmute.lms.lmsapi.enums.CalculationScope;
import com.hcmute.lms.lmsapi.enums.CalculationStatus;
import java.time.Instant;
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

/** Lượt chạy tính toán đo lường chuẩn đầu ra 4 cấp độ (collection calculation_runs, F06). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "calculation_runs")
@CompoundIndexes({
  @CompoundIndex(name = "calc_scope_started_idx", def = "{'scope': 1, 'startedAt': -1}"),
  @CompoundIndex(
      name = "calc_sections_started_idx",
      def = "{'scopeRef.sectionIds': 1, 'startedAt': -1}"),
  @CompoundIndex(
      name = "uniq_running_lockKey",
      def = "{'lockKey': 1}",
      unique = true,
      partialFilter = "{'status': {'$in': ['PENDING', 'PROCESSING']}}")
})
public class CalculationRun {

  @Id private String id;

  @Field("scope")
  private CalculationScope scope;

  @Field("scopeRef")
  private ScopeRef scopeRef;

  @Field("lockKey")
  private String lockKey;

  @Field(name = "programVersionId", targetType = FieldType.OBJECT_ID)
  private String programVersionId;

  @Builder.Default
  @Field(name = "syllabusIds", targetType = FieldType.OBJECT_ID)
  private List<String> syllabusIds = new ArrayList<>();

  @Field("isOfficial")
  private boolean official;

  @Field("dataSnapshot")
  private DataSnapshot dataSnapshot;

  @Field("isStale")
  private boolean stale;

  @Field("staleReason")
  private String staleReason;

  @Builder.Default
  @Field("status")
  private CalculationStatus status = CalculationStatus.PENDING;

  @Field("errorMessage")
  private String errorMessage;

  @Field(name = "triggeredBy", targetType = FieldType.OBJECT_ID)
  private String triggeredBy;

  @Field("startedAt")
  private Instant startedAt;

  @Field("finishedAt")
  private Instant finishedAt;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ScopeRef {
    @Builder.Default
    @Field(name = "sectionIds", targetType = FieldType.OBJECT_ID)
    private List<String> sectionIds = new ArrayList<>();

    @Builder.Default
    @Field(name = "courseIds", targetType = FieldType.OBJECT_ID)
    private List<String> courseIds = new ArrayList<>();

    @Builder.Default
    @Field("cohorts")
    private List<String> cohorts = new ArrayList<>();

    @Field(name = "termId", targetType = FieldType.OBJECT_ID)
    private String termId;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class DataSnapshot {
    @Field("scoreChecksum")
    private String scoreChecksum;

    @Field("totalScores")
    private Integer totalScores;

    @Field("lastScoreUpdatedAt")
    private Instant lastScoreUpdatedAt;
  }
}
