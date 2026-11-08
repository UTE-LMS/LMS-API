package com.hcmute.lms.lmsapi.service;

import com.hcmute.lms.lmsapi.dto.AssessmentItemsRequest;
import com.hcmute.lms.lmsapi.dto.AssessmentRequest;
import com.hcmute.lms.lmsapi.dto.AssessmentResponse;
import com.hcmute.lms.lmsapi.dto.AssessmentUpdateRequest;
import com.hcmute.lms.lmsapi.dto.CoverageResponse;
import java.util.List;

/** Khai báo bài đánh giá: khung theo đề cương và bản của từng lớp (F04 / US-15, US-16). */
public interface AssessmentService {

  List<AssessmentResponse> getTemplates(String syllabusId);

  AssessmentResponse createTemplate(String syllabusId, AssessmentRequest request);

  List<AssessmentResponse> getSectionAssessments(String sectionId);

  /**
   * Tạo bài đánh giá cho một lớp, khai báo items trực tiếp hoặc sao chép từ khung ({@code
   * templateId}). Bản lớp là SNAPSHOT: sau khi tạo, sửa khung hay đổi CLO không làm đổi bản lớp.
   */
  AssessmentResponse createSectionAssessment(String sectionId, AssessmentRequest request);

  AssessmentResponse getAssessment(String id);

  AssessmentResponse updateAssessment(String id, AssessmentUpdateRequest request);

  /** Thay toàn bộ items[]; bị từ chối khi cấu trúc đã khóa hoặc đang chờ duyệt. */
  AssessmentResponse replaceItems(String id, AssessmentItemsRequest request);

  /** Độ phủ theo CLO và Bloom, tính lại từ items hiện tại, kèm mã CLO để dễ đọc. */
  CoverageResponse getCoverage(String id);
}
