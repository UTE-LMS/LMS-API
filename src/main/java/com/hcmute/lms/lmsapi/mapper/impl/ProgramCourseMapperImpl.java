package com.hcmute.lms.lmsapi.mapper.impl;

import com.hcmute.lms.lmsapi.document.Course;
import com.hcmute.lms.lmsapi.document.ProgramCourse;
import com.hcmute.lms.lmsapi.dto.CourseResponse;
import com.hcmute.lms.lmsapi.dto.ProgramCourseResponse;
import com.hcmute.lms.lmsapi.mapper.CourseMapper;
import com.hcmute.lms.lmsapi.mapper.ProgramCourseMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProgramCourseMapperImpl implements ProgramCourseMapper {

  private final CourseMapper courseMapper;

  @Override
  public ProgramCourseResponse toResponse(ProgramCourse programCourse) {
    return toResponse(programCourse, null, Collections.emptyMap());
  }

  @Override
  public ProgramCourseResponse toResponse(ProgramCourse programCourse, Course course) {
    return toResponse(programCourse, course, Collections.emptyMap());
  }

  @Override
  public ProgramCourseResponse toResponse(
      ProgramCourse programCourse, Course course, Map<String, Course> relatedCoursesById) {
    if (programCourse == null) {
      return null;
    }
    Map<String, Course> lookup =
        relatedCoursesById != null ? relatedCoursesById : Collections.emptyMap();
    List<String> prereqIds =
        programCourse.getPrerequisiteCourseIds() != null
            ? new ArrayList<>(programCourse.getPrerequisiteCourseIds())
            : new ArrayList<>();
    List<String> previousIds =
        programCourse.getPreviousCourseIds() != null
            ? new ArrayList<>(programCourse.getPreviousCourseIds())
            : new ArrayList<>();
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
        .prerequisiteCourseIds(prereqIds)
        .prerequisiteCodes(resolveCourseCodes(prereqIds, lookup))
        .previousCourseIds(previousIds)
        .previousCourseCodes(resolveCourseCodes(previousIds, lookup))
        .build();
  }

  private List<String> resolveCourseCodes(List<String> ids, Map<String, Course> lookup) {
    if (ids.isEmpty() || lookup.isEmpty()) {
      return new ArrayList<>();
    }
    List<String> codes = new ArrayList<>();
    for (String id : ids) {
      Course related = id != null ? lookup.get(id) : null;
      if (related != null && related.getCode() != null) {
        codes.add(related.getCode());
      }
    }
    return codes;
  }

  @Override
  public List<ProgramCourseResponse> toResponseList(List<ProgramCourse> programCourses) {
    if (programCourses == null) {
      return new ArrayList<>();
    }
    return programCourses.stream().map(this::toResponse).toList();
  }
}
