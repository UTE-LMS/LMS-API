package com.hcmute.lms.lmsapi.document;

import com.hcmute.lms.lmsapi.enums.StudentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * Sinh viên trong collection students (§4.18 / US-20).
 *
 * <p>Riêng tư (Q09): {@code studentRef} là mã dùng để nhận diện, mã thật chỉ trên CSDL thật/staging
 * còn dữ liệu mẫu và test dùng mã giả. {@code fullName}, {@code email}, {@code userId}, {@code
 * programVersionId} của schema chưa được dùng ở F05a vì chưa có tính năng nào cần đến và dữ liệu cá
 * nhân chỉ lưu khi được phép; thêm khi có yêu cầu.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "students")
public class Student {

  @Id private String id;

  /** Mã thay thế của sinh viên (duy nhất toàn hệ thống). */
  @Indexed(unique = true)
  @Field("studentRef")
  private String studentRef;

  /** Khóa/niên khóa (tùy chọn). */
  @Field("cohort")
  private String cohort;

  /** Mã ngành (tùy chọn). */
  @Field("majorCode")
  private String majorCode;

  @Builder.Default
  @Field("status")
  private StudentStatus status = StudentStatus.ACTIVE;
}
