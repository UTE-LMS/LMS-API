package com.hcmute.lms.lmsapi.enums;

/** Các kiểu phân loại khung chương trình đào tạo trong chiều phân tích 5 (Milestone 1). */
public enum CurriculumClassificationType {

  /** Phân loại PLO theo nhóm (GENERAL vs SPECIALIZED). */
  PLO_CATEGORY,

  /** Phân loại học phần theo tính chất (COMPULSORY vs ELECTIVE). */
  COURSE_TYPE,

  /** Phân bố theo thang đo nhận thức Bloom (1 đến 6 - chỉ gom nhóm, không quy đổi điểm số). */
  BLOOM_LEVEL
}
