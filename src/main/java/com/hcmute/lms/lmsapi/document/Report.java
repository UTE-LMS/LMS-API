package com.hcmute.lms.lmsapi.document;

import com.hcmute.lms.lmsapi.enums.ReportType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

/** Báo cáo đã sinh cho QA / Giảng viên / Quản lý CTĐT (collection reports, F07). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "reports")
@CompoundIndexes({
  @CompoundIndex(name = "report_run_idx", def = "{'runId': 1}"),
  @CompoundIndex(
      name = "report_type_pv_created_idx",
      def = "{'type': 1, 'programVersionId': 1, 'createdAt': -1}")
})
public class Report {

  @Id private String id;

  @Field("type")
  private ReportType type;

  @Field("title")
  private String title;

  @Builder.Default
  @Field("params")
  private Map<String, Object> params = new LinkedHashMap<>();

  @Field(name = "runId", targetType = FieldType.OBJECT_ID)
  private String runId;

  @Field(name = "programVersionId", targetType = FieldType.OBJECT_ID)
  private String programVersionId;

  @Builder.Default
  @Field("summary")
  private Map<String, Object> summary = new LinkedHashMap<>();

  @Field("limitations")
  private String limitations;

  @Builder.Default
  @Field("exports")
  private List<ReportExport> exports = new ArrayList<>();

  @Field(name = "generatedBy", targetType = FieldType.OBJECT_ID)
  private String generatedBy;

  @Field("createdAt")
  private Instant createdAt;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ReportExport {
    @Field("format")
    private String format;

    @Field("fileRef")
    private String fileRef;

    @Field("generatedAt")
    private Instant generatedAt;
  }
}
