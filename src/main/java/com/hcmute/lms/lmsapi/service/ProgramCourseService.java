package com.hcmute.lms.lmsapi.service;

import com.hcmute.lms.lmsapi.dto.ProgramCourseRequest;
import com.hcmute.lms.lmsapi.dto.ProgramCourseResponse;
import com.hcmute.lms.lmsapi.dto.ProgramCourseUpdateRequest;
import java.util.List;

public interface ProgramCourseService {

  List<ProgramCourseResponse> getCoursesByProgramVersion(String programVersionId);

  ProgramCourseResponse getProgramCourse(String programVersionId, String courseId);

  ProgramCourseResponse addCourseToProgramVersion(
      String programVersionId, ProgramCourseRequest request);

  ProgramCourseResponse updateProgramCourse(
      String programVersionId, String courseId, ProgramCourseUpdateRequest request);

  void removeCourseFromProgramVersion(String programVersionId, String courseId);
}
