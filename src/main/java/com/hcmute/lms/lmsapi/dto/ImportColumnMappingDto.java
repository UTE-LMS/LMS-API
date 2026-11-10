package com.hcmute.lms.lmsapi.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Ánh xạ cột tệp điểm (US-21/AC-2). Khi gửi lên: {@code studentColumn} tùy chọn (mặc định
 * "studentRef"), mỗi mục có {@code item} là {@code _id} hoặc {@code externalRef} của câu hỏi/tiêu
 * chí. Khi trả về: {@code item} luôn là {@code _id} của câu hỏi/tiêu chí.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportColumnMappingDto {

  private String studentColumn;

  @Builder.Default private List<Item> items = new ArrayList<>();

  /** Một cột điểm trong tệp và câu hỏi/tiêu chí nó ứng với. */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Item {

    private String column;
    private String item;
  }
}
