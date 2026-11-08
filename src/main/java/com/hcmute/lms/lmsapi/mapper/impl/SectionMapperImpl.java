package com.hcmute.lms.lmsapi.mapper.impl;

import com.hcmute.lms.lmsapi.document.CourseSection;
import com.hcmute.lms.lmsapi.dto.SectionRequest;
import com.hcmute.lms.lmsapi.dto.SectionResponse;
import com.hcmute.lms.lmsapi.enums.SectionStatus;
import com.hcmute.lms.lmsapi.mapper.SectionMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SectionMapperImpl implements SectionMapper {

  @Override
  public CourseSection toDocument(SectionRequest request, List<String> lecturerIds) {
    if (request == null) {
      return null;
    }
    return CourseSection.builder()
        .code(request.getCode().trim())
        .courseId(request.getCourseId())
        .syllabusId(request.getSyllabusId())
        .termId(request.getTermId())
        .lecturerIds(new ArrayList<>(lecturerIds))
        .enrolledCount(0)
        .status(SectionStatus.OPEN)
        .build();
  }

  @Override
  public SectionResponse toResponse(CourseSection section) {
    if (section == null) {
      return null;
    }
    return SectionResponse.builder()
        .id(section.getId())
        .code(section.getCode())
        .courseId(section.getCourseId())
        .syllabusId(section.getSyllabusId())
        .termId(section.getTermId())
        .lecturerIds(
            section.getLecturerIds() != null
                ? new ArrayList<>(section.getLecturerIds())
                : new ArrayList<>())
        .enrolledCount(section.getEnrolledCount())
        .status(section.getStatus())
        .build();
  }

  @Override
  public List<SectionResponse> toResponseList(List<CourseSection> sections) {
    if (sections == null || sections.isEmpty()) {
      return Collections.emptyList();
    }
    return sections.stream().map(this::toResponse).toList();
  }
}
