package com.hcmute.lms.lmsapi.dto;

import com.hcmute.lms.lmsapi.enums.AssessmentPhase;
import com.hcmute.lms.lmsapi.enums.AssessmentType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Yêu cầu tạo bài đánh giá (ADR 0014, v0.7). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentRequest {

  @NotBlank(message = "Mã bài đánh giá không được để trống")
  @Pattern(
      regexp = "^[A-Za-z0-9._-]{1,40}$",
      message = "Mã bài đánh giá chỉ gồm chữ cái, số, dấu chấm, gạch ngang, gạch dưới (1-40 ký tự)")
  private String code;

  @NotBlank(message = "Tên bài đánh giá không được để trống")
  @Size(max = 200, message = "Tên bài đánh giá dài tối đa 200 ký tự")
  private String name;

  private AssessmentType type;

  private AssessmentPhase phase;

  private Instant scheduledAt;

  @Pattern(regexp = "^[0-9a-fA-F]{24}$", message = "ID khung phải là ObjectId 24 ký tự hex")
  private String templateId;

  private List<@Valid AssessmentCloWeightDto> cloWeights;

  private List<@Valid AssessmentItemDto> items;
}
