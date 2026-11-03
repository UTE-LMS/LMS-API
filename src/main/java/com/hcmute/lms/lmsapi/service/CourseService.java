package com.hcmute.lms.lmsapi.service;

import com.hcmute.lms.lmsapi.dto.CourseRequest;
import com.hcmute.lms.lmsapi.dto.CourseResponse;
import com.hcmute.lms.lmsapi.dto.CourseUpdateRequest;
import com.hcmute.lms.lmsapi.dto.PageResponse;

public interface CourseService {

  PageResponse<CourseResponse> getCourses(int page, int size, String search);

  CourseResponse getCourseById(String id);

  CourseResponse createCourse(CourseRequest request);

  CourseResponse updateCourse(String id, CourseUpdateRequest request);

  void deleteCourse(String id);
}
