package com.hcmute.lms.lmsapi.document;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

/**
 * Ánh xạ cột tệp điểm (import_batches.columnMapping): cột sinh viên và danh sách (cột điểm, câu
 * hỏi/tiêu chí). Lưu dạng danh sách chứ không dạng map vì tên cột trong tệp có thể chứa dấu chấm
 * (ví dụ "Câu 1.5"), mà khóa của map trong MongoDB không được chứa dấu chấm.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportColumnMapping {

  /** Tên cột chứa mã sinh viên (studentRef) trong tệp. */
  @Field("studentColumn")
  private String studentColumn;

  @Builder.Default
  @Field("items")
  private List<Entry> items = new ArrayList<>();

  /** Một cột điểm trong tệp ứng với một phần tử assessments.items[]. */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Entry {

    @Field("column")
    private String column;

    @Field(name = "itemId", targetType = FieldType.OBJECT_ID)
    private String itemId;
  }
}
