package com.hcmute.lms.lmsapi.document;

import com.hcmute.lms.lmsapi.enums.TermStatus;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/** Document đại diện cho học kỳ trong collection terms (§4.16 / US-18). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "terms")
public class Term {

  @Id private String id;

  /** Mã học kỳ (duy nhất toàn hệ thống, ví dụ 2026-HK1). */
  @Indexed(unique = true)
  @Field("code")
  private String code;

  /** Niên khóa học kỳ (ví dụ 2025-2026). */
  @Field("academicYear")
  private String academicYear;

  /** Thời điểm bắt đầu học kỳ. */
  @Field("startDate")
  private Instant startDate;

  /** Thời điểm kết thúc học kỳ. */
  @Field("endDate")
  private Instant endDate;

  /** Trạng thái học kỳ (OPEN, SCORE_LOCKED, CLOSED). */
  @Builder.Default
  @Field("status")
  private TermStatus status = TermStatus.OPEN;
}
