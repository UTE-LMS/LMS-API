package com.hcmute.lms.lmsapi.service;

import com.hcmute.lms.lmsapi.dto.AssessmentScoreTemplate;
import com.hcmute.lms.lmsapi.dto.PageResponse;
import com.hcmute.lms.lmsapi.dto.ProgramCourseCsvImportResponse;
import com.hcmute.lms.lmsapi.dto.ProgramCourseRequest;
import com.hcmute.lms.lmsapi.dto.ProgramCourseResponse;
import com.hcmute.lms.lmsapi.dto.ProgramCourseUpdateRequest;
import com.hcmute.lms.lmsapi.enums.CourseCategory;
import com.hcmute.lms.lmsapi.enums.CourseType;
import org.springframework.web.multipart.MultipartFile;

public interface ProgramCourseService {

  PageResponse<ProgramCourseResponse> getCoursesByProgramVersion(
      String programVersionId,
      Integer semester,
      CourseType type,
      CourseCategory category,
      String search,
      int page,
      int size);

  ProgramCourseResponse getProgramCourse(String programVersionId, String courseId);

  ProgramCourseResponse addCourseToProgramVersion(
      String programVersionId, ProgramCourseRequest request);

  ProgramCourseResponse updateProgramCourse(
      String programVersionId, String courseId, ProgramCourseUpdateRequest request);

  void removeCourseFromProgramVersion(String programVersionId, String courseId);

  AssessmentScoreTemplate generateTemplateCsv();

  ProgramCourseCsvImportResponse importProgramCoursesCsv(
      String programVersionId, MultipartFile file);
}
