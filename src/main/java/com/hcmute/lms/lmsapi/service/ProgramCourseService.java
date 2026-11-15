package com.hcmute.lms.lmsapi.service;

import com.hcmute.lms.lmsapi.dto.ProgramCourseRequest;
import com.hcmute.lms.lmsapi.dto.ProgramCourseResponse;
import com.hcmute.lms.lmsapi.dto.ProgramCourseUpdateRequest;
import com.hcmute.lms.lmsapi.enums.CourseCategory;
import com.hcmute.lms.lmsapi.enums.CourseType;
import java.util.List;

public interface ProgramCourseService {

  List<ProgramCourseResponse> getCoursesByProgramVersion(String programVersionId);

  default List<ProgramCourseResponse> getCoursesByProgramVersion(
      String programVersionId,
      Integer semester,
      CourseType type,
      CourseCategory category,
      String search) {
    return getCoursesByProgramVersion(programVersionId);
  }

  ProgramCourseResponse getProgramCourse(String programVersionId, String courseId);

  ProgramCourseResponse addCourseToProgramVersion(
      String programVersionId, ProgramCourseRequest request);

  ProgramCourseResponse updateProgramCourse(
      String programVersionId, String courseId, ProgramCourseUpdateRequest request);

  void removeCourseFromProgramVersion(String programVersionId, String courseId);
}
