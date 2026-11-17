package com.hcmute.lms.lmsapi.service;

import com.hcmute.lms.lmsapi.dto.AssessmentAlignmentResponse;

/** Kiểm tra bộ đề của lớp học phần có bám sát chuẩn đầu ra (CLO → PI → PLO) hay không. */
public interface AssessmentAlignmentService {

  AssessmentAlignmentResponse getSectionAlignment(String sectionId);
}
