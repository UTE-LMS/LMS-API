package com.hcmute.lms.lmsapi.mapper.impl;

import com.hcmute.lms.lmsapi.document.CourseSection;
import com.hcmute.lms.lmsapi.document.User;
import com.hcmute.lms.lmsapi.dto.SectionRequest;
import com.hcmute.lms.lmsapi.dto.SectionResponse;
import com.hcmute.lms.lmsapi.enums.SectionStatus;
import com.hcmute.lms.lmsapi.mapper.SectionMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
    return toResponse(section, Collections.emptyMap());
  }

  @Override
  public SectionResponse toResponse(CourseSection section, Map<String, User> lecturersById) {
    if (section == null) {
      return null;
    }
    List<String> lecturerIds =
        section.getLecturerIds() != null
            ? new ArrayList<>(section.getLecturerIds())
            : new ArrayList<>();
    List<SectionResponse.LecturerSummary> lecturers = new ArrayList<>();
    if (lecturersById != null && !lecturersById.isEmpty()) {
      for (String lecturerId : lecturerIds) {
        User lecturer = lecturersById.get(lecturerId);
        if (lecturer != null) {
          lecturers.add(
              SectionResponse.LecturerSummary.builder()
                  .id(lecturer.getId())
                  .username(lecturer.getUsername())
                  .fullName(lecturer.getFullName())
                  .build());
        }
      }
    }
    return SectionResponse.builder()
        .id(section.getId())
        .code(section.getCode())
        .courseId(section.getCourseId())
        .syllabusId(section.getSyllabusId())
        .termId(section.getTermId())
        .lecturerIds(lecturerIds)
        .lecturers(lecturers)
        .enrolledCount(section.getEnrolledCount())
        .status(section.getStatus())
        .build();
  }

  @Override
  public List<SectionResponse> toResponseList(List<CourseSection> sections) {
    return toResponseList(sections, Collections.emptyMap());
  }

  @Override
  public List<SectionResponse> toResponseList(
      List<CourseSection> sections, Map<String, User> lecturersById) {
    if (sections == null || sections.isEmpty()) {
      return Collections.emptyList();
    }
    Map<String, User> lookup = lecturersById != null ? lecturersById : Collections.emptyMap();
    return sections.stream().map(s -> toResponse(s, lookup)).toList();
  }
}
