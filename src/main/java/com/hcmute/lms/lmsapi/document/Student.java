package com.hcmute.lms.lmsapi.document;

import com.hcmute.lms.lmsapi.enums.StudentStatus;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "students")
public class Student {

  @Id private String id;

  /** Mã số sinh viên (duy nhất toàn hệ thống). */
  @Indexed(unique = true)
  @Field("studentCode")
  private String studentCode;

  /** Họ và tên sinh viên (tùy chọn). */
  @Field("fullName")
  private String fullName;

  /** Khóa/niên khóa (tùy chọn). */
  @Field("cohort")
  private String cohort;

  @Builder.Default
  @Field("status")
  private StudentStatus status = StudentStatus.ACTIVE;

  @CreatedDate
  @Field("createdAt")
  private Instant createdAt;

  @LastModifiedDate
  @Field("updatedAt")
  private Instant updatedAt;
}
