package com.hcmute.lms.lmsapi.dto.analytics;

import com.hcmute.lms.lmsapi.enums.AnalyticsDimension;
import com.hcmute.lms.lmsapi.enums.AssessmentType;
import com.hcmute.lms.lmsapi.enums.CurriculumClassificationType;
import com.hcmute.lms.lmsapi.enums.ResultLevel;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Tham số yêu cầu truy vấn phân tích dữ liệu đa chiều (Milestone 1). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsQueryRequest {

  /** Chiều phân tích chính (bắt buộc). */
  @NotNull(message = "Chiều phân tích (dimension) không được để trống")
  private AnalyticsDimension dimension;

  /**
   * Định danh phiên bản chương trình đào tạo (bắt buộc với chiều COHORT,
   * CURRICULUM_CLASSIFICATION).
   */
  private String programVersionId;

  /** Định danh học phần (bắt buộc với chiều COURSE_SECTIONS). */
  private String courseId;

  /** Danh sách học kỳ cần lọc hoặc đối sánh. */
  private List<String> termIds;

  /** Danh sách lớp học phần cần so sánh. */
  private List<String> sectionIds;

  /** Danh sách khóa tuyển sinh cần phân tích. */
  private List<String> cohorts;

  /** Danh sách các loại bài đánh giá (QUIZ, MIDTERM, FINAL...). */
  private List<AssessmentType> assessmentTypes;

  /** Cấp độ kết quả đo lường (ASSESSMENT_CLO, CLO, PI, PLO). Mặc định tự động theo từng chiều. */
  private ResultLevel level;

  /**
   * Kiểu phân loại khi phân tích theo khung chương trình (PLO_CATEGORY, COURSE_TYPE, BLOOM_LEVEL).
   */
  private CurriculumClassificationType classificationType;

  /**
   * Định danh lượt tính toán cụ thể. Nếu không truyền, hệ thống tự lấy lượt tính SUCCESS mới nhất.
   */
  private String runId;

  /** Định danh giảng viên cần lọc (tùy chọn, dùng cho chiều COURSE_LECTURER). */
  private String lecturerId;

  /** Danh sách định danh giảng viên cần đối sánh (tùy chọn, dùng cho chiều COURSE_LECTURER). */
  private List<String> lecturerIds;

  /** Định danh sinh viên cần phân tích (tùy chọn, dùng cho chiều STUDENT). */
  private String studentId;

  /** Danh sách định danh sinh viên cần phân tích (tùy chọn, dùng cho chiều STUDENT). */
  private List<String> studentIds;

  /**
   * Chỉ giữ các dòng là CLO/PI/PLO có id hoặc mã nằm trong danh sách này (ví dụ PLO1, PI1.2). Không
   * áp dụng cho chiều CURRICULUM_CLASSIFICATION (dòng là nhóm phân loại).
   */
  private List<String> subjectIds;

  /**
   * Lọc theo kết quả đạt chuẩn: true giữ các dòng/cột có ít nhất 1 ô có người Đạt; false giữ các
   * dòng/cột có ít nhất 1 ô có người Chưa đạt. Null: không lọc.
   */
  private Boolean passed;

  /**
   * Chỉ số trang (0-indexed), áp dụng khi truy vấn ma trận với dimension = STUDENT (mặc định 0).
   */
  private Integer page;

  /**
   * Kích thước trang (1..100), áp dụng khi truy vấn ma trận với dimension = STUDENT (mặc định 20).
   */
  private Integer size;

  /**
   * Tìm kiếm sinh viên theo mã SV hoặc họ tên (không phân biệt hoa thường) cho dimension = STUDENT.
   */
  private String search;
}
