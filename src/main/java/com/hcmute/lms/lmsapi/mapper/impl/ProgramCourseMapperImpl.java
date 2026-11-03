package com.hcmute.lms.lmsapi.mapper.impl;

import com.hcmute.lms.lmsapi.document.Course;
import com.hcmute.lms.lmsapi.document.ProgramCourse;
import com.hcmute.lms.lmsapi.dto.CourseResponse;
import com.hcmute.lms.lmsapi.dto.ProgramCourseResponse;
import com.hcmute.lms.lmsapi.mapper.CourseMapper;
import com.hcmute.lms.lmsapi.mapper.ProgramCourseMapper;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProgramCourseMapperImpl implements ProgramCourseMapper {

  private final CourseMapper courseMapper;

  @Override
  public ProgramCourseResponse toResponse(ProgramCourse programCourse) {
    return toResponse(programCourse, null);
  }

  @Override
  public ProgramCourseResponse toResponse(ProgramCourse programCourse, Course course) {
    if (programCourse == null) {
      return null;
    }
    CourseResponse courseResponse = course != null ? courseMapper.toResponse(course) : null;
    return ProgramCourseResponse.builder()
        .id(programCourse.getId())
        .programVersionId(programCourse.getProgramVersionId())
        .courseId(programCourse.getCourseId())
        .course(courseResponse)
        .semester(programCourse.getSemester())
        .credits(programCourse.getCredits())
        .type(programCourse.getType())
        .category(programCourse.getCategory())
        .prerequisiteCourseIds(
            programCourse.getPrerequisiteCourseIds() != null
                ? new ArrayList<>(programCourse.getPrerequisiteCourseIds())
                : new ArrayList<>())
        .previousCourseIds(
            programCourse.getPreviousCourseIds() != null
                ? new ArrayList<>(programCourse.getPreviousCourseIds())
                : new ArrayList<>())
        .build();
  }

  @Override
  public List<ProgramCourseResponse> toResponseList(List<ProgramCourse> programCourses) {
    if (programCourses == null) {
      return new ArrayList<>();
    }
    return programCourses.stream().map(this::toResponse).toList();
  }
}
