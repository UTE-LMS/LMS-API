package com.hcmute.lms.lmsapi.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Yêu cầu thay toàn bộ items[] của một bài đánh giá (US-15). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentItemsRequest {

  @NotNull(message = "Danh sách items không được để trống")
  private List<@Valid AssessmentItemDto> items;
}
