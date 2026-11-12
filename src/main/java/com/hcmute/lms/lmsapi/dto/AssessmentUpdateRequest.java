package com.hcmute.lms.lmsapi.dto;

import com.hcmute.lms.lmsapi.enums.AssessmentPhase;
import com.hcmute.lms.lmsapi.enums.AssessmentType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Yêu cầu cập nhật thông tin bài đánh giá (ADR 0014, v0.7).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentUpdateRequest {

  @NotBlank(message = "Tên bài đánh giá không được để trống")
  @Size(max = 200, message = "Tên bài đánh giá dài tối đa 200 ký tự")
  private String name;

  private AssessmentType type;

  private AssessmentPhase phase;

  private Instant scheduledAt;

  private List<@Valid AssessmentCloWeightDto> cloWeights;
}
