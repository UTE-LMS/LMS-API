package com.hcmute.lms.lmsapi.service;

import com.hcmute.lms.lmsapi.dto.AssessmentRejectRequest;
import com.hcmute.lms.lmsapi.dto.AssessmentResponse;
import com.hcmute.lms.lmsapi.dto.UserPrincipal;

/**
 * Quy trình duyệt ma trận đề thi của bài đánh giá bản lớp (NEW-01, ADR 0001 phương án A): giảng
 * viên gửi duyệt, Trưởng bộ môn duyệt hoặc từ chối. Chỉ duyệt ma trận ánh xạ chuẩn đầu ra, không
 * duyệt nội dung câu hỏi.
 */
public interface AssessmentApprovalService {

  /** DRAFT -> SUBMITTED (NEW-01/AC-1, AC-2). */
  AssessmentResponse submit(String id, UserPrincipal actor);

  /**
   * SUBMITTED -> APPROVED; khóa cấu trúc và ghi {@code audit_logs} ASSESSMENT_APPROVE (AC-3). Người
   * gửi duyệt không được tự duyệt (AC-5).
   */
  AssessmentResponse approve(String id, UserPrincipal actor);

  /**
   * SUBMITTED -> REJECTED kèm ghi chú bắt buộc; ghi {@code audit_logs} ASSESSMENT_REJECT (AC-4).
   */
  AssessmentResponse reject(String id, AssessmentRejectRequest request, UserPrincipal actor);
}
