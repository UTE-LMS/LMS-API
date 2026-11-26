package com.hcmute.lms.lmsapi.mapper.impl;

import com.hcmute.lms.lmsapi.document.Course;
import com.hcmute.lms.lmsapi.document.Syllabus;
import com.hcmute.lms.lmsapi.document.Term;
import com.hcmute.lms.lmsapi.document.Verification;
import com.hcmute.lms.lmsapi.dto.SyllabusRequest;
import com.hcmute.lms.lmsapi.dto.SyllabusResponse;
import com.hcmute.lms.lmsapi.enums.SyllabusStatus;
import com.hcmute.lms.lmsapi.enums.VerificationStatus;
import com.hcmute.lms.lmsapi.mapper.SyllabusMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SyllabusMapperImpl implements SyllabusMapper {

  @Override
  public Syllabus toDocument(String courseId, SyllabusRequest request) {
    if (request == null) {
      return null;
    }
    return Syllabus.builder()
        .courseId(courseId)
        .version(request.getVersion() != null ? request.getVersion().trim() : null)
        .effectiveFromTermId(request.getEffectiveFromTermId())
        .effectiveToTermId(request.getEffectiveToTermId())
        .status(SyllabusStatus.CONFIRMED)
        .verification(Verification.builder().status(VerificationStatus.VERIFIED).build())
        .extra(request.getExtra())
        .build();
  }

  @Override
  public SyllabusResponse toResponse(Syllabus syllabus) {
    return toResponse(syllabus, null, null, null);
  }

  @Override
  public SyllabusResponse toResponse(
      Syllabus syllabus, Course course, Term effectiveFromTerm, Term effectiveToTerm) {
    if (syllabus == null) {
      return null;
    }
    return SyllabusResponse.builder()
        .id(syllabus.getId())
        .courseId(syllabus.getCourseId())
        .courseCode(course != null ? course.getCode() : null)
        .courseName(course != null ? course.getName() : null)
        .version(syllabus.getVersion())
        .effectiveFromTermId(syllabus.getEffectiveFromTermId())
        .effectiveFromTermCode(effectiveFromTerm != null ? effectiveFromTerm.getCode() : null)
        .effectiveToTermId(syllabus.getEffectiveToTermId())
        .effectiveToTermCode(effectiveToTerm != null ? effectiveToTerm.getCode() : null)
        .status(syllabus.getStatus())
        .verification(syllabus.getVerification())
        .extra(syllabus.getExtra())
        .build();
  }

  @Override
  public List<SyllabusResponse> toResponseList(List<Syllabus> syllabi) {
    if (syllabi == null || syllabi.isEmpty()) {
      return Collections.emptyList();
    }
    List<SyllabusResponse> responses = new ArrayList<>(syllabi.size());
    for (Syllabus syllabus : syllabi) {
      responses.add(toResponse(syllabus));
    }
    return responses;
  }
}
