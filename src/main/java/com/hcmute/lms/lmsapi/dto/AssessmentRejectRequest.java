package com.hcmute.lms.lmsapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Yêu cầu từ chối ma trận bài đánh giá; ghi chú là bắt buộc (NEW-01/AC-4). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentRejectRequest {

  @NotBlank(message = "Ghi chú từ chối (reviewNote) không được để trống")
  @Size(max = 1000, message = "Ghi chú từ chối dài tối đa 1000 ký tự")
  private String reviewNote;
}
