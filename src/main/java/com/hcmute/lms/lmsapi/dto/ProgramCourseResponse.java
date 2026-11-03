package com.hcmute.lms.lmsapi.dto;

import com.hcmute.lms.lmsapi.enums.CourseCategory;
import com.hcmute.lms.lmsapi.enums.CourseType;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProgramCourseResponse {

  private String id;
  private String programVersionId;
  private String courseId;
  private CourseResponse course;
  private Integer semester;
  private Integer credits;
  private CourseType type;
  private CourseCategory category;
  @Builder.Default private List<String> prerequisiteCourseIds = new ArrayList<>();
}
