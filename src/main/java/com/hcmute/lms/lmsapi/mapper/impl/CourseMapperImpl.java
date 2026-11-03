package com.hcmute.lms.lmsapi.mapper.impl;

import com.hcmute.lms.lmsapi.document.Course;
import com.hcmute.lms.lmsapi.dto.CourseResponse;
import com.hcmute.lms.lmsapi.mapper.CourseMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CourseMapperImpl implements CourseMapper {

  @Override
  public CourseResponse toResponse(Course course) {
    if (course == null) {
      return null;
    }
    return CourseResponse.builder()
        .id(course.getId())
        .code(course.getCode())
        .name(course.getName())
        .credits(course.getCredits())
        .extra(course.getExtra())
        .build();
  }

  @Override
  public List<CourseResponse> toResponseList(List<Course> courses) {
    if (courses == null) {
      return new ArrayList<>();
    }
    return courses.stream().map(this::toResponse).toList();
  }
}
