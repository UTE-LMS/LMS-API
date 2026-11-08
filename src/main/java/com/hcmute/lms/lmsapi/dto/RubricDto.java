package com.hcmute.lms.lmsapi.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Rubric của một tiêu chí: các mức 1-4 (US-16). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RubricDto {

  @NotEmpty(message = "Rubric phải có ít nhất một mức")
  private List<@Valid RubricLevelDto> levels;
}
