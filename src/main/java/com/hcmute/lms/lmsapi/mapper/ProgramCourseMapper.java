package com.hcmute.lms.lmsapi.mapper;

import com.hcmute.lms.lmsapi.document.Course;
import com.hcmute.lms.lmsapi.document.ProgramCourse;
import com.hcmute.lms.lmsapi.dto.ProgramCourseResponse;
import java.util.List;

public interface ProgramCourseMapper {

  ProgramCourseResponse toResponse(ProgramCourse programCourse);

  ProgramCourseResponse toResponse(ProgramCourse programCourse, Course course);

  List<ProgramCourseResponse> toResponseList(List<ProgramCourse> programCourses);
}
