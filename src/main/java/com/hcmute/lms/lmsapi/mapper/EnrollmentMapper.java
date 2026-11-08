package com.hcmute.lms.lmsapi.mapper;

import com.hcmute.lms.lmsapi.document.Enrollment;
import com.hcmute.lms.lmsapi.document.Student;
import com.hcmute.lms.lmsapi.dto.EnrollmentResponse;

public interface EnrollmentMapper {

  /** Ghép bản ghi ghi danh với thông tin sinh viên (student có thể null nếu dữ liệu mồ côi). */
  EnrollmentResponse toResponse(Enrollment enrollment, Student student);
}
