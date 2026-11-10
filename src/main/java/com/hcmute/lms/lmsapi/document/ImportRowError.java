package com.hcmute.lms.lmsapi.document;

import com.hcmute.lms.lmsapi.enums.ImportErrorType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

/** Lỗi của một dòng/cột trong tệp điểm (§4.21 / US-22). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "import_row_errors")
@CompoundIndex(name = "import_row_errors_batch_row_idx", def = "{'batchId': 1, 'rowNumber': 1}")
public class ImportRowError {

  @Id private String id;

  @Field(name = "batchId", targetType = FieldType.OBJECT_ID)
  private String batchId;

  /** Số dòng vật lý trong tệp gốc (dòng tiêu đề là dòng 1). */
  @Field("rowNumber")
  private int rowNumber;

  /** Tên cột trong tệp; null khi lỗi của cả dòng. */
  @Field("column")
  private String column;

  @Field("errorType")
  private ImportErrorType errorType;

  @Field("rawValue")
  private String rawValue;

  @Field("message")
  private String message;
}
