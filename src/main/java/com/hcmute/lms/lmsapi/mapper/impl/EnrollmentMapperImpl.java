package com.hcmute.lms.lmsapi.mapper.impl;

import com.hcmute.lms.lmsapi.document.Enrollment;
import com.hcmute.lms.lmsapi.document.Student;
import com.hcmute.lms.lmsapi.dto.EnrollmentResponse;
import com.hcmute.lms.lmsapi.mapper.EnrollmentMapper;
import org.springframework.stereotype.Component;

@Component
public class EnrollmentMapperImpl implements EnrollmentMapper {

  @Override
  public EnrollmentResponse toResponse(Enrollment enrollment, Student student) {
    if (enrollment == null) {
      return null;
    }
    return EnrollmentResponse.builder()
        .id(enrollment.getId())
        .sectionId(enrollment.getSectionId())
        .studentId(enrollment.getStudentId())
        .studentRef(student != null ? student.getStudentRef() : null)
        .cohort(student != null ? student.getCohort() : null)
        .majorCode(student != null ? student.getMajorCode() : null)
        .status(enrollment.getStatus())
        .enrolledAt(enrollment.getEnrolledAt())
        .build();
  }
}
