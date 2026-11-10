package com.hcmute.lms.lmsapi.mapper;

import com.hcmute.lms.lmsapi.document.Assessment;
import com.hcmute.lms.lmsapi.document.AssessmentItem;
import com.hcmute.lms.lmsapi.document.CoverageSummary;
import com.hcmute.lms.lmsapi.dto.AssessmentItemDto;
import com.hcmute.lms.lmsapi.dto.AssessmentResponse;
import com.hcmute.lms.lmsapi.dto.CoverageResponse;
import java.util.List;
import java.util.Map;

public interface AssessmentMapper {

  /**
   * Đổi các phần tử đã được kiểm tra sang document. Phần tử có {@code id} thì giữ, chưa có thì sinh
   * ObjectId mới; {@code orderIndex} bỏ trống thì lấy theo vị trí (bắt đầu từ 1).
   */
  List<AssessmentItem> toItems(List<AssessmentItemDto> dtos);

  /** Đổi phần tử đã lưu sang DTO không mang id, dùng để sao chép khung sang bản lớp. */
  List<AssessmentItemDto> toCopyDtos(List<AssessmentItem> items);

  AssessmentResponse toResponse(Assessment assessment);

  List<AssessmentResponse> toResponseList(List<Assessment> assessments);

  /**
   * Đổi độ phủ sang phản hồi.
   *
   * @param cloCodes ánh xạ cloId -> mã CLO để hiển thị; null nếu không cần mã
   */
  CoverageResponse toCoverageResponse(CoverageSummary summary, Map<String, String> cloCodes);
}
