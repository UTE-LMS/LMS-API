package com.hcmute.lms.lmsapi.dto;

import com.hcmute.lms.lmsapi.enums.CalculationScope;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Yêu cầu chạy tính toán đo lường chuẩn đầu ra 4 cấp độ (F06). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalculationRunRequest {

  @NotNull(message = "Phạm vi tính toán (scope) không được để trống")
  private CalculationScope scope;

  private String programVersionId;

  private List<String> sectionIds;

  private List<String> courseIds;

  private List<String> cohorts;

  private String termId;

  @Builder.Default private boolean official = true;
}
