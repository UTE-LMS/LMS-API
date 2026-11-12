package com.hcmute.lms.lmsapi.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

/**
 * CLO mà câu hỏi đo lường và điểm tối đa của câu hỏi dành cho CLO đó (v0.7).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutcomeMapping {

  @Field(name = "cloId", targetType = FieldType.OBJECT_ID)
  private String cloId;

  /** Điểm tối đa của câu hỏi đối với CLO này, lớn hơn 0. */
  @Field("maxScore")
  private Double maxScore;
}
