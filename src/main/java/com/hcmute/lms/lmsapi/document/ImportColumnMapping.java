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
 * Ánh xạ cột tệp điểm: cột sinh viên và danh sách (cột điểm, itemId, cloId).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportColumnMapping {

  @Field("studentColumn")
  private String studentColumn;

  @Builder.Default
  @Field("items")
  private List<Entry> items = new ArrayList<>();

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Entry {

    @Field("column")
    private String column;

    @Field(name = "itemId", targetType = FieldType.OBJECT_ID)
    private String itemId;

    @Field(name = "cloId", targetType = FieldType.OBJECT_ID)
    private String cloId;
  }
}
