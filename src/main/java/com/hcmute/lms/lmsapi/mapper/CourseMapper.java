package com.hcmute.lms.lmsapi.mapper;

import com.hcmute.lms.lmsapi.document.Course;
import com.hcmute.lms.lmsapi.dto.CourseResponse;
import java.util.List;

public interface CourseMapper {

  CourseResponse toResponse(Course course);

  List<CourseResponse> toResponseList(List<Course> courses);
}
