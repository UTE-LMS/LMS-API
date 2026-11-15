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
}
