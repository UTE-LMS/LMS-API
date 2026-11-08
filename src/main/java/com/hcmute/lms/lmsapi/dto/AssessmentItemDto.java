package com.hcmute.lms.lmsapi.dto;

import com.hcmute.lms.lmsapi.enums.ItemKind;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Một câu hỏi/tiêu chí của bài đánh giá, dùng cho cả yêu cầu và phản hồi (US-15, US-16). Ở yêu cầu,
 * {@code id} là tùy chọn: có thì giữ nguyên định danh phần tử, không có thì hệ thống tự sinh.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentItemDto {

  @Pattern(regexp = "^[0-9a-fA-F]{24}$", message = "ID phần tử phải là ObjectId 24 ký tự hex")
  private String id;

  @NotBlank(message = "Mã phần tử không được để trống")
  @Pattern(
      regexp = "^[A-Za-z0-9._-]{1,40}$",
      message = "Mã phần tử chỉ gồm chữ cái, số, dấu chấm, gạch ngang, gạch dưới (1-40 ký tự)")
  private String code;

  @Min(value = 1, message = "Thứ tự phải từ 1 trở lên")
  private Integer orderIndex;

  @Size(max = 300, message = "Tiêu đề dài tối đa 300 ký tự")
  private String title;

  @NotNull(message = "Loại phần tử (QUESTION/CRITERION) không được để trống")
  private ItemKind kind;

  @NotNull(message = "Điểm tối đa không được để trống")
  @DecimalMin(value = "0", inclusive = false, message = "Điểm tối đa phải lớn hơn 0")
  private Double maxScore;

  @Min(value = 1, message = "Mức Bloom phải từ 1 đến 6")
  @Max(value = 6, message = "Mức Bloom phải từ 1 đến 6")
  private Integer bloomLevel;

  @Size(max = 200, message = "Chủ đề dài tối đa 200 ký tự")
  private String topic;

  @Size(max = 100, message = "Mã tham chiếu ngoài dài tối đa 100 ký tự")
  private String externalRef;

  @NotEmpty(message = "Mỗi phần tử phải đo ít nhất một CLO")
  private List<@Valid OutcomeMappingDto> outcomeMappings;

  @Valid private RubricDto rubric;
}
