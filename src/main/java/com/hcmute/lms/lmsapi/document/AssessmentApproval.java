package com.hcmute.lms.lmsapi.document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

/** Thông tin gửi duyệt/duyệt ma trận của bài đánh giá bản lớp (NEW-01). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentApproval {

  @Field(name = "submittedBy", targetType = FieldType.OBJECT_ID)
  private String submittedBy;

  @Field("submittedAt")
  private Instant submittedAt;

  @Field(name = "reviewedBy", targetType = FieldType.OBJECT_ID)
  private String reviewedBy;

  @Field("reviewedAt")
  private Instant reviewedAt;

  @Field("reviewNote")
  private String reviewNote;

  @Builder.Default
  @Field("history")
  private List<ApprovalHistoryEntry> history = new ArrayList<>();
}
