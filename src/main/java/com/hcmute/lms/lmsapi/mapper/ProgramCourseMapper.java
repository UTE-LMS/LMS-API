package com.hcmute.lms.lmsapi.mapper;

import com.hcmute.lms.lmsapi.document.Course;
import com.hcmute.lms.lmsapi.document.ProgramCourse;
import com.hcmute.lms.lmsapi.dto.ProgramCourseResponse;
import java.util.List;
import java.util.Map;

public interface ProgramCourseMapper {

  ProgramCourseResponse toResponse(ProgramCourse programCourse);

  ProgramCourseResponse toResponse(ProgramCourse programCourse, Course course);

  ProgramCourseResponse toResponse(
      ProgramCourse programCourse, Course course, Map<String, Course> relatedCoursesById);

  List<ProgramCourseResponse> toResponseList(List<ProgramCourse> programCourses);
}
