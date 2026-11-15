package com.hcmute.lms.lmsapi.dto.analytics;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Tiêu đề dòng hoặc cột trong ma trận phân tích đa chiều (Milestone 1). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatrixHeader {

  /** Định danh duy nhất của phần tử (ObjectId hoặc mã nhóm). */
  private String id;

  /** Mã hiển thị ngắn gọn (ví dụ: "CLO1", "OOPR230279_01", "2025-HK1", "K24"). */
  private String code;

  /** Tên đầy đủ hoặc nhãn mô tả. */
  private String name;

  /** Mức Bloom nhận thức (nếu là CLO). */
  private Integer bloomLevel;

  /** Thông tin mở rộng tùy chọn. */
  private Map<String, Object> extra;
}
