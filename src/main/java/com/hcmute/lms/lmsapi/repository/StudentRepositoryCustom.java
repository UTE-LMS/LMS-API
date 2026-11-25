package com.hcmute.lms.lmsapi.repository;

import com.hcmute.lms.lmsapi.document.Student;
import com.hcmute.lms.lmsapi.enums.StudentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface StudentRepositoryCustom {

  Page<Student> searchStudents(
      String search, String cohort, StudentStatus status, Pageable pageable);
}
