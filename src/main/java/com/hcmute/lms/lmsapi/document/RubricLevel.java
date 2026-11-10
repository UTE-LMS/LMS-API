package com.hcmute.lms.lmsapi.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;

/** Một mức của rubric. {@code level} (1-4) là mức rubric, tách khỏi mức Bloom (1-6). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RubricLevel {

  @Field("level")
  private Integer level;

  @Field("description")
  private String description;

  /** Điểm của mức này, không vượt quá maxScore của tiêu chí. */
  @Field("score")
  private Double score;
}
