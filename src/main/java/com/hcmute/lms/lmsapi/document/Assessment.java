package com.hcmute.lms.lmsapi.document;

import com.hcmute.lms.lmsapi.enums.AssessmentPhase;
import com.hcmute.lms.lmsapi.enums.AssessmentScope;
import com.hcmute.lms.lmsapi.enums.AssessmentStatus;
import com.hcmute.lms.lmsapi.enums.AssessmentType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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

/**
 * Bài đánh giá trong collection assessments (ADR 0014, Schema v0.7).
 * Vòng đời: DRAFT -> LOCKED -> SCORE_ENTERED.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "assessments")
@CompoundIndexes({
  @CompoundIndex(
      name = "assessments_syllabus_section_code_idx",
      def = "{'syllabusId': 1, 'sectionId': 1, 'code': 1}",
      unique = true),
  @CompoundIndex(name = "assessments_section_status_idx", def = "{'sectionId': 1, 'status': 1}")
})
public class Assessment {

  @Id private String id;

  @Field(name = "syllabusId", targetType = FieldType.OBJECT_ID)
  private String syllabusId;

  /** Chỉ có với bản lớp (scope = SECTION). */
  @Field(name = "sectionId", targetType = FieldType.OBJECT_ID)
  private String sectionId;

  /** Khung mà bản lớp được sao chép từ đó (nếu có). */
  @Field(name = "templateId", targetType = FieldType.OBJECT_ID)
  private String templateId;

  @Field("code")
  private String code;

  @Field("name")
  private String name;

  @Field("scope")
  private AssessmentScope scope;

  @Field("type")
  private AssessmentType type;

  @Field("phase")
  private AssessmentPhase phase;

  @Field("scheduledAt")
  private Instant scheduledAt;

  @Builder.Default
  @Field("status")
  private AssessmentStatus status = AssessmentStatus.DRAFT;

  /** true khi status thuộc LOCKED / SCORE_ENTERED. Luôn được ghi cùng lúc với status. */
  @Field("isLocked")
  private boolean locked;

  /** Tỷ lệ đóng góp W_{p,j} của bài kiểm tra cho từng CLO có mặt trong bài. */
  @Builder.Default
  @Field("cloWeights")
  private List<AssessmentCloWeight> cloWeights = new ArrayList<>();

  /** Do service tính từ items, không cho người dùng nhập tay. */
  @Field("coverageSummary")
  private CoverageSummary coverageSummary;

  @Builder.Default
  @Field("items")
  private List<AssessmentItem> items = new ArrayList<>();

  /**
   * Khóa lạc quan.
   */
  @Version
  @Field("revision")
  private Long revision;
}
