package com.hcmute.lms.lmsapi.document;

import com.hcmute.lms.lmsapi.enums.EnrollmentStatus;
import java.time.Instant;
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

/**
 * Ghi danh của một sinh viên vào một lớp học phần (§4.19 / US-20). Số bản ghi {@code ENROLLED} của
 * lớp là mẫu số của tỷ lệ đạt.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "enrollments")
@CompoundIndexes({
  @CompoundIndex(
      name = "section_student_unique_idx",
      def = "{'sectionId': 1, 'studentId': 1}",
      unique = true)
})
public class Enrollment {

  @Id private String id;

  @Field(name = "sectionId", targetType = FieldType.OBJECT_ID)
  private String sectionId;

  @Field(name = "studentId", targetType = FieldType.OBJECT_ID)
  private String studentId;

  @Builder.Default
  @Field("status")
  private EnrollmentStatus status = EnrollmentStatus.ENROLLED;

  @Field("enrolledAt")
  private Instant enrolledAt;
}
