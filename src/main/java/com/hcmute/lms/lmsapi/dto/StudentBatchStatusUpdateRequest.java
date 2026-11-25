package com.hcmute.lms.lmsapi.dto;

import com.hcmute.lms.lmsapi.enums.StudentStatus;
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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentBatchStatusUpdateRequest {

  @NotEmpty(message = "Danh sách sinh viên không được để trống")
  @Size(max = 500, message = "Chỉ được cập nhật tối đa 500 sinh viên mỗi lần")
  private List<
          @NotBlank(message = "ID sinh viên không được để trống")
          @Pattern(
              regexp = "^[0-9a-fA-F]{24}$",
              message = "ID sinh viên phải là ObjectId 24 ký tự hex")
          String>
      studentIds;

  @NotNull(message = "Trạng thái sinh viên không được để trống")
  private StudentStatus status;
}
