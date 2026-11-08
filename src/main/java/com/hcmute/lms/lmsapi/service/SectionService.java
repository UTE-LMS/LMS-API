package com.hcmute.lms.lmsapi.service;

import com.hcmute.lms.lmsapi.dto.SectionRequest;
import com.hcmute.lms.lmsapi.dto.SectionResponse;
import com.hcmute.lms.lmsapi.dto.SectionStatusUpdateRequest;
import com.hcmute.lms.lmsapi.dto.SectionUpdateRequest;
import com.hcmute.lms.lmsapi.dto.UserPrincipal;
import java.util.List;

/** Quản lý lớp học phần (F05a / US-19). */
public interface SectionService {

  /**
   * Liệt kê lớp học phần mà người gọi được phép đọc (US-19/AC-4: giảng viên chỉ thấy lớp mình phụ
   * trách), có thể lọc theo học kỳ và học phần.
   *
   * @param termId lọc theo học kỳ (null: không lọc)
   * @param courseId lọc theo học phần (null: không lọc)
   */
  List<SectionResponse> getSections(UserPrincipal principal, String termId, String courseId);

  SectionResponse getSection(String id);

  SectionResponse createSection(SectionRequest request);

  SectionResponse updateLecturers(String id, SectionUpdateRequest request);

  SectionResponse updateStatus(String id, SectionStatusUpdateRequest request, String actorUsername);
}
