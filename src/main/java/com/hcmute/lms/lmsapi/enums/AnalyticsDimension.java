package com.hcmute.lms.lmsapi.enums;

/** 5 chiều phân tích động phục vụ báo cáo ma trận chuẩn đầu ra (Milestone 1). */
public enum AnalyticsDimension {

  /** Chiều 1: Xu hướng theo Thời gian / Học kỳ (termId, academicYear). */
  TIME_TERM,

  /** Chiều 2: So sánh song song các lớp trong cùng học phần (courseId × sectionId). */
  COURSE_SECTIONS,

  /** Chiều 3: Đối sánh theo khóa tuyển sinh (cohort). */
  COHORT,

  /** Chiều 4: Phân rã theo thành phần đánh giá (ASSESSMENT_CLO theo assessment.type). */
  ASSESSMENT_COMPONENT,

  /**
   * Chiều 5: Phân loại chương trình đào tạo (PLO Category, Course Type, hoặc phân bố Bloom level).
   */
  CURRICULUM_CLASSIFICATION
}
