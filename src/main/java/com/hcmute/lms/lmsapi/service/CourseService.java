package com.hcmute.lms.lmsapi.service;

import com.hcmute.lms.lmsapi.dto.CourseImportResultResponse;
import com.hcmute.lms.lmsapi.dto.CourseRequest;
import com.hcmute.lms.lmsapi.dto.CourseResponse;
import com.hcmute.lms.lmsapi.dto.CourseUpdateRequest;
import com.hcmute.lms.lmsapi.dto.PageResponse;
import org.springframework.web.multipart.MultipartFile;

public interface CourseService {

  PageResponse<CourseResponse> getCourses(int page, int size, String search);

  CourseResponse getCourseById(String id);

  CourseResponse createCourse(CourseRequest request);

  CourseResponse updateCourse(String id, CourseUpdateRequest request);

  void deleteCourse(String id);

  CourseImportResultResponse importCourses(MultipartFile file);
}
