package com.hcmute.lms.lmsapi.document;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

/**
 * Một câu hỏi trong ma trận đề thi (assessments.items[]).
 * Điểm tối đa được lưu trực tiếp trong outcomeMappings[].maxScore (v0.7).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentItem {

  @Field(name = "_id", targetType = FieldType.OBJECT_ID)
  private String id;

  /** Mã duy nhất trong một bài đánh giá (ví dụ: Q1, Câu 1). */
  @Field("code")
  private String code;

  @Field("orderIndex")
  private Integer orderIndex;

  /** Mức Bloom 1-6. */
  @Field("bloomLevel")
  private Integer bloomLevel;

  /** CLO mà phần tử đo, kèm điểm tối đa tương ứng. */
  @Builder.Default
  @Field("outcomeMappings")
  private List<OutcomeMapping> outcomeMappings = new ArrayList<>();
}
