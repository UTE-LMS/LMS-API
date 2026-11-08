package com.hcmute.lms.lmsapi.document;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;

/** Rubric của một tiêu chí: các mức 1-4, mỗi mức có mô tả và điểm. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentRubric {

  @Builder.Default
  @Field("levels")
  private List<RubricLevel> levels = new ArrayList<>();
}
