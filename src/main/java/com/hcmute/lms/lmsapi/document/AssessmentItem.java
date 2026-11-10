package com.hcmute.lms.lmsapi.document;

import com.hcmute.lms.lmsapi.enums.ItemKind;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

/** Một câu hỏi hoặc tiêu chí rubric trong bài đánh giá (assessments.items[]). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentItem {

  @Field(name = "_id", targetType = FieldType.OBJECT_ID)
  private String id;

  /** Mã duy nhất trong một bài đánh giá. */
  @Field("code")
  private String code;

  @Field("orderIndex")
  private Integer orderIndex;

  @Field("title")
  private String title;

  @Field("kind")
  private ItemKind kind;

  /** Điểm tối đa của phần tử, lớn hơn 0. */
  @Field("maxScore")
  private Double maxScore;

  /** Mức Bloom 1-6. Độc lập với điểm số và với mức rubric (rule 02). */
  @Field("bloomLevel")
  private Integer bloomLevel;

  @Field("topic")
  private String topic;

  /** Mã câu hỏi trên Moodle/LMS để khớp cột điểm khi nhập (F05b). */
  @Field("externalRef")
  private String externalRef;

  /** CLO mà phần tử đo, kèm phần điểm chia cho từng CLO (Σ weight = 1, ADR 0011). */
  @Builder.Default
  @Field("outcomeMappings")
  private List<OutcomeMapping> outcomeMappings = new ArrayList<>();

  @Field("rubric")
  private AssessmentRubric rubric;
}
