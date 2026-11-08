package com.hcmute.lms.lmsapi.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Yêu cầu cập nhật lớp học phần (US-19). Chỉ đổi được danh sách giảng viên phụ trách; học phần, đề
 * cương và học kỳ là bất biến vì bài đánh giá, ghi danh và điểm của lớp bám theo chúng.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SectionUpdateRequest {

  @NotEmpty(message = "Lớp học phần phải có ít nhất một giảng viên phụ trách")
  private List<
          @Pattern(
              regexp = "^[0-9a-fA-F]{24}$",
              message = "ID giảng viên phải là ObjectId 24 ký tự hex")
          String>
      lecturerIds;
}
