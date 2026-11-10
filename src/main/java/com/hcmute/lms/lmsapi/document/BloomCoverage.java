package com.hcmute.lms.lmsapi.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;

/** Số phần tử, điểm và phần trăm điểm của bài đánh giá ở một mức Bloom. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BloomCoverage {

  @Field("bloomLevel")
  private Integer bloomLevel;

  @Field("questionCount")
  private Integer questionCount;

  @Field("points")
  private Double points;

  @Field("percentage")
  private Double percentage;
}
