package com.hcmute.lms.lmsapi.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

/** Điểm và phần trăm điểm của bài đánh giá thuộc về một CLO. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CloCoverage {

  @Field(name = "cloId", targetType = FieldType.OBJECT_ID)
  private String cloId;

  @Field("points")
  private Double points;

  @Field("percentage")
  private Double percentage;
}
