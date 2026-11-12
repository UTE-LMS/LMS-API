package com.hcmute.lms.lmsapi.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Một câu hỏi của bài đánh giá (v0.7). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentItemDto {

  @Pattern(regexp = "^[0-9a-fA-F]{24}$", message = "ID phần tử phải là ObjectId 24 ký tự hex")
  private String id;

  @NotBlank(message = "Mã phần tử không được để trống")
  @Pattern(
      regexp = "^[A-Za-z0-9._ -]{1,40}$",
      message =
          "Mã phần tử chỉ gồm chữ cái, số, dấu chấm, gạch ngang, gạch dưới, khoảng trắng (1-40 ký tự)")
  private String code;

  @Min(value = 1, message = "Thứ tự phải từ 1 trở lên")
  private Integer orderIndex;

  @Min(value = 1, message = "Mức Bloom phải từ 1 đến 6")
  @Max(value = 6, message = "Mức Bloom phải từ 1 đến 6")
  private Integer bloomLevel;

  @NotEmpty(message = "Mỗi phần tử phải đo ít nhất một CLO")
  private List<@Valid OutcomeMappingDto> outcomeMappings;
}
