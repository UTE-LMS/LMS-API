package com.hcmute.lms.lmsapi.service;

import com.hcmute.lms.lmsapi.dto.AssessmentItemsRequest;
import com.hcmute.lms.lmsapi.dto.AssessmentRequest;
import com.hcmute.lms.lmsapi.dto.AssessmentResponse;
import com.hcmute.lms.lmsapi.dto.AssessmentUpdateRequest;
import com.hcmute.lms.lmsapi.dto.CoverageResponse;
import java.util.List;

/** Khai báo bài đánh giá: khung theo đề cương và bản của từng lớp (ADR 0014, DB Schema v0.7). */
public interface AssessmentService {

  List<AssessmentResponse> getTemplates(String syllabusId);

  AssessmentResponse createTemplate(String syllabusId, AssessmentRequest request);

  List<AssessmentResponse> getSectionAssessments(String sectionId);

  AssessmentResponse createSectionAssessment(String sectionId, AssessmentRequest request);

  AssessmentResponse getAssessment(String id);

  AssessmentResponse updateAssessment(String id, AssessmentUpdateRequest request);

  AssessmentResponse replaceItems(String id, AssessmentItemsRequest request);

  CoverageResponse getCoverage(String id);

  AssessmentResponse lock(String id, String actorId);

  AssessmentResponse unlock(String id, String actorId);

  void deleteAssessment(String id, String actorId);
}
