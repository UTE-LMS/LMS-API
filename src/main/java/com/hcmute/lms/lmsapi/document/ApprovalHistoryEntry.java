package com.hcmute.lms.lmsapi.document;

import com.hcmute.lms.lmsapi.enums.ApprovalAction;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

/** Một dòng lịch sử duyệt: ai làm gì lúc nào, kèm ghi chú (bắt buộc khi từ chối). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalHistoryEntry {

  @Field("action")
  private ApprovalAction action;

  @Field(name = "actorId", targetType = FieldType.OBJECT_ID)
  private String actorId;

  @Field("at")
  private Instant at;

  @Field("note")
  private String note;
}
