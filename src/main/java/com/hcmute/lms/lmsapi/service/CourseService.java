package com.hcmute.lms.lmsapi.service;

import com.hcmute.lms.lmsapi.dto.CourseImportResultResponse;
import com.hcmute.lms.lmsapi.dto.CourseRequest;
import com.hcmute.lms.lmsapi.dto.CourseResponse;
import com.hcmute.lms.lmsapi.dto.CourseUpdateRequest;
import com.hcmute.lms.lmsapi.dto.PageResponse;
import org.springframework.web.multipart.MultipartFile;

public interface CourseService {

  default PageResponse<CourseResponse> getCourses(int page, int size, String search) {
    return getCourses(page, size, search, null);
  }

  PageResponse<CourseResponse> getCourses(int page, int size, String search, String facultyId);

  default PageResponse<CourseResponse> getCourses(
      int page, int size, String search, String facultyId, Boolean isActive) {
    return getCourses(page, size, search, facultyId);
  }

  CourseResponse getCourseById(String id);

  CourseResponse createCourse(CourseRequest request);

  CourseResponse updateCourse(String id, CourseUpdateRequest request);

  void deleteCourse(String id);

  default CourseImportResultResponse importCourses(MultipartFile file) {
    return importCourses(file, null);
  }

  CourseImportResultResponse importCourses(MultipartFile file, String facultyId);
}
