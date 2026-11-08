package com.hcmute.lms.lmsapi.dto;

import com.hcmute.lms.lmsapi.enums.AssessmentPhase;
import com.hcmute.lms.lmsapi.enums.AssessmentType;
import com.hcmute.lms.lmsapi.enums.DataGranularity;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Yêu cầu cập nhật thông tin bài đánh giá (không gồm items, xem {@link AssessmentItemsRequest}). Là
 * thay thế toàn phần: trường tùy chọn để trống nghĩa là bỏ giá trị. Mã bài đánh giá bất biến.
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

  @PositiveOrZero(message = "Tổng điểm không được âm")
  private Double totalPoints;

  @DecimalMin(value = "0", message = "Tỷ trọng phải nằm trong [0, 1]")
  @DecimalMax(value = "1", message = "Tỷ trọng phải nằm trong [0, 1]")
  private Double weightInCourse;

  private DataGranularity dataGranularity;
}
