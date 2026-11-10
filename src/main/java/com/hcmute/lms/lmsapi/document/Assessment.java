package com.hcmute.lms.lmsapi.document;

import com.hcmute.lms.lmsapi.enums.AssessmentPhase;
import com.hcmute.lms.lmsapi.enums.AssessmentScope;
import com.hcmute.lms.lmsapi.enums.AssessmentStatus;
import com.hcmute.lms.lmsapi.enums.AssessmentType;
import com.hcmute.lms.lmsapi.enums.DataGranularity;
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
 * Bài đánh giá trong collection assessments (§4.15 / F04, US-15, US-16, NEW-01). Có hai dạng: khung
 * theo đề cương ({@code SYLLABUS_TEMPLATE}) và bản của một lớp ({@code SECTION}); chỉ bản lớp đi
 * qua quy trình duyệt ma trận. {@code items[].outcomeMappings} của bản lớp là bản sao (snapshot)
 * tại thời điểm khai báo, nên đổi CLO về sau không làm đổi bài đã thi.
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

  /** Tổng điểm khai báo; phải khớp Σ items.maxScore (dung sai 0,001) khi gửi duyệt. */
  @Field("totalPoints")
  private Double totalPoints;

  /** Tỷ trọng của bài trong điểm học phần, trong [0, 1]. */
  @Field("weightInCourse")
  private Double weightInCourse;

  @Builder.Default
  @Field("dataGranularity")
  private DataGranularity dataGranularity = DataGranularity.ITEM;

  @Builder.Default
  @Field("status")
  private AssessmentStatus status = AssessmentStatus.DRAFT;

  /** true khi status thuộc APPROVED/SCORE_ENTERED/LOCKED; luôn được ghi cùng lúc với status. */
  @Field("isLocked")
  private boolean locked;

  @Field("approval")
  private AssessmentApproval approval;

  /** Do service tính từ items, không cho người dùng nhập tay. */
  @Field("coverageSummary")
  private CoverageSummary coverageSummary;

  @Builder.Default
  @Field("items")
  private List<AssessmentItem> items = new ArrayList<>();

  /**
   * Khóa lạc quan: hai người cùng duyệt một bài thì chỉ một người ghi thành công, người còn lại
   * nhận xung đột phiên bản (HTTP 409).
   */
  @Version
  @Field("revision")
  private Long revision;
}
