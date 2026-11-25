package com.hcmute.lms.lmsapi.service;

import com.hcmute.lms.lmsapi.dto.AssessmentScoreTemplate;
import com.hcmute.lms.lmsapi.dto.CsvImportResultResponse;
import com.hcmute.lms.lmsapi.dto.PageResponse;
import com.hcmute.lms.lmsapi.dto.SectionRequest;
import com.hcmute.lms.lmsapi.dto.SectionResponse;
import com.hcmute.lms.lmsapi.dto.SectionStatusUpdateRequest;
import com.hcmute.lms.lmsapi.dto.SectionUpdateRequest;
import com.hcmute.lms.lmsapi.dto.UserPrincipal;
import com.hcmute.lms.lmsapi.dto.UserResponse;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/** Quản lý lớp học phần (F05a / US-19). */
public interface SectionService {

  /**
   * Liệt kê lớp học phần có phân trang mà người gọi được phép đọc (US-19/AC-4: giảng viên chỉ thấy
   * lớp mình phụ trách), hỗ trợ lọc theo học kỳ, học phần và tìm theo mã lớp.
   *
   * @param termId lọc theo học kỳ (null: không lọc)
   * @param courseId lọc theo học phần (null: không lọc)
   * @param search tìm theo mã lớp (không phân biệt hoa thường, null: không lọc)
   * @param page chỉ số trang (0-indexed)
   * @param size kích thước trang (1..100)
   */
  PageResponse<SectionResponse> getSections(
      UserPrincipal principal, String termId, String courseId, String search, int page, int size);

  /**
   * Liệt kê giảng viên đang hoạt động ({@code Role.LECTURER}, {@code UserStatus.ACTIVE}), có thể
   * lọc theo Khoa.
   *
   * @param facultyId lọc theo Khoa (null: lấy tất cả giảng viên đang hoạt động)
   */
  List<UserResponse> getActiveLecturers(String facultyId);

  SectionResponse getSection(String id);

  SectionResponse createSection(SectionRequest request);

  SectionResponse updateLecturers(String id, SectionUpdateRequest request);

  SectionResponse updateStatus(String id, SectionStatusUpdateRequest request, String actorUsername);

  AssessmentScoreTemplate generateTemplateCsv();

  CsvImportResultResponse importSections(MultipartFile file, UserPrincipal principal);
}
