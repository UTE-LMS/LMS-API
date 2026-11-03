package com.hcmute.lms.lmsapi.document;

import com.hcmute.lms.lmsapi.enums.CourseCategory;
import com.hcmute.lms.lmsapi.enums.CourseType;
import java.util.ArrayList;
import java.util.List;
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
@Document(collection = "program_courses")
@CompoundIndexes({
  @CompoundIndex(
      name = "pv_course_unique_idx",
      def = "{'programVersionId': 1, 'courseId': 1}",
      unique = true),
  @CompoundIndex(name = "course_idx", def = "{'courseId': 1}")
})
public class ProgramCourse {

  @Id private String id;

  @Field(name = "programVersionId", targetType = FieldType.OBJECT_ID)
  private String programVersionId;

  @Field(name = "courseId", targetType = FieldType.OBJECT_ID)
  private String courseId;

  @Field("semester")
  private Integer semester;

  @Field("credits")
  private Integer credits;

  @Field("type")
  private CourseType type;

  @Field("category")
  private CourseCategory category;

  @Builder.Default
  @Field(name = "prerequisiteCourseIds", targetType = FieldType.OBJECT_ID)
  private List<String> prerequisiteCourseIds = new ArrayList<>();

  @Builder.Default
  @Field(name = "previousCourseIds", targetType = FieldType.OBJECT_ID)
  private List<String> previousCourseIds = new ArrayList<>();
}
