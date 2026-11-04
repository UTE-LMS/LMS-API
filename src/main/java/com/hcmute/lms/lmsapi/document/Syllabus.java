package com.hcmute.lms.lmsapi.document;

import com.hcmute.lms.lmsapi.enums.SyllabusStatus;
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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "syllabi")
@CompoundIndexes({
  @CompoundIndex(
      name = "syllabi_course_version_idx",
      def = "{'courseId': 1, 'version': 1}",
      unique = true)
})
public class Syllabus {

  @Id private String id;

  @Field(name = "courseId", targetType = FieldType.OBJECT_ID)
  private String courseId;

  @Field("version")
  private String version;

  @Field(name = "effectiveFromTermId", targetType = FieldType.OBJECT_ID)
  private String effectiveFromTermId;

  @Field(name = "effectiveToTermId", targetType = FieldType.OBJECT_ID)
  private String effectiveToTermId;

  @Field("status")
  private SyllabusStatus status;

  @Field("verification")
  private Verification verification;

  @Field("extra")
  private Map<String, Object> extra;
}
