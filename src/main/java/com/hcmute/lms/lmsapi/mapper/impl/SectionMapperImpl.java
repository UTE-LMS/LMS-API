package com.hcmute.lms.lmsapi.mapper.impl;

import com.hcmute.lms.lmsapi.document.Course;
import com.hcmute.lms.lmsapi.document.CourseSection;
import com.hcmute.lms.lmsapi.document.Syllabus;
import com.hcmute.lms.lmsapi.document.Term;
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
    return toResponse(
        section,
        Collections.emptyMap(),
        Collections.emptyMap(),
        Collections.emptyMap(),
        Collections.emptyMap());
  }

  @Override
  public SectionResponse toResponse(CourseSection section, Map<String, User> lecturersById) {
    return toResponse(
        section,
        lecturersById,
        Collections.emptyMap(),
        Collections.emptyMap(),
        Collections.emptyMap());
  }

  @Override
  public SectionResponse toResponse(
      CourseSection section,
      Map<String, User> lecturersById,
      Map<String, Term> termsById,
      Map<String, Course> coursesById,
      Map<String, Syllabus> syllabiById) {
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
    Term term =
        termsById != null && section.getTermId() != null
            ? termsById.get(section.getTermId())
            : null;
    Course course =
        coursesById != null && section.getCourseId() != null
            ? coursesById.get(section.getCourseId())
            : null;
    Syllabus syllabus =
        syllabiById != null && section.getSyllabusId() != null
            ? syllabiById.get(section.getSyllabusId())
            : null;
    return SectionResponse.builder()
        .id(section.getId())
        .code(section.getCode())
        .courseId(section.getCourseId())
        .courseCode(course != null ? course.getCode() : null)
        .courseName(course != null ? course.getName() : null)
        .syllabusId(section.getSyllabusId())
        .syllabusVersion(syllabus != null ? syllabus.getVersion() : null)
        .syllabusStatus(syllabus != null ? syllabus.getStatus() : null)
        .termId(section.getTermId())
        .termCode(term != null ? term.getCode() : null)
        .lecturerIds(lecturerIds)
        .lecturers(lecturers)
        .enrolledCount(section.getEnrolledCount())
        .status(section.getStatus())
        .build();
  }

  @Override
  public List<SectionResponse> toResponseList(List<CourseSection> sections) {
    return toResponseList(
        sections,
        Collections.emptyMap(),
        Collections.emptyMap(),
        Collections.emptyMap(),
        Collections.emptyMap());
  }

  @Override
  public List<SectionResponse> toResponseList(
      List<CourseSection> sections, Map<String, User> lecturersById) {
    return toResponseList(
        sections,
        lecturersById,
        Collections.emptyMap(),
        Collections.emptyMap(),
        Collections.emptyMap());
  }

  @Override
  public List<SectionResponse> toResponseList(
      List<CourseSection> sections,
      Map<String, User> lecturersById,
      Map<String, Term> termsById,
      Map<String, Course> coursesById,
      Map<String, Syllabus> syllabiById) {
    if (sections == null || sections.isEmpty()) {
      return Collections.emptyList();
    }
    Map<String, User> lecturerLookup =
        lecturersById != null ? lecturersById : Collections.emptyMap();
    Map<String, Term> termLookup = termsById != null ? termsById : Collections.emptyMap();
    Map<String, Course> courseLookup = coursesById != null ? coursesById : Collections.emptyMap();
    Map<String, Syllabus> syllabusLookup =
        syllabiById != null ? syllabiById : Collections.emptyMap();
    return sections.stream()
        .map(s -> toResponse(s, lecturerLookup, termLookup, courseLookup, syllabusLookup))
        .toList();
  }
}
