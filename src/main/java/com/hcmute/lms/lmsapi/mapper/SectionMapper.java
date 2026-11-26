package com.hcmute.lms.lmsapi.mapper;

import com.hcmute.lms.lmsapi.document.Course;
import com.hcmute.lms.lmsapi.document.CourseSection;
import com.hcmute.lms.lmsapi.document.Syllabus;
import com.hcmute.lms.lmsapi.document.Term;
import com.hcmute.lms.lmsapi.document.User;
import com.hcmute.lms.lmsapi.dto.SectionRequest;
import com.hcmute.lms.lmsapi.dto.SectionResponse;
import java.util.List;
import java.util.Map;

public interface SectionMapper {

  /** Dựng document mới từ yêu cầu; lecturerIds đã được service khử trùng lặp trước khi truyền. */
  CourseSection toDocument(SectionRequest request, List<String> lecturerIds);

  SectionResponse toResponse(CourseSection section);

  SectionResponse toResponse(CourseSection section, Map<String, User> lecturersById);

  SectionResponse toResponse(
      CourseSection section,
      Map<String, User> lecturersById,
      Map<String, Term> termsById,
      Map<String, Course> coursesById,
      Map<String, Syllabus> syllabiById);

  List<SectionResponse> toResponseList(List<CourseSection> sections);

  List<SectionResponse> toResponseList(
      List<CourseSection> sections, Map<String, User> lecturersById);

  List<SectionResponse> toResponseList(
      List<CourseSection> sections,
      Map<String, User> lecturersById,
      Map<String, Term> termsById,
      Map<String, Course> coursesById,
      Map<String, Syllabus> syllabiById);
}
