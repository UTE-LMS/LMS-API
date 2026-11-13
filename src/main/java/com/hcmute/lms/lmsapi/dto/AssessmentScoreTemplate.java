package com.hcmute.lms.lmsapi.dto;

/** Kết quả sinh tệp CSV mẫu nhập điểm cho bài đánh giá (F05b, OBE v0.7). */
public record AssessmentScoreTemplate(String fileName, byte[] content) {

  public AssessmentScoreTemplate {
    content = content != null ? content.clone() : null;
  }

  @Override
  public byte[] content() {
    return content != null ? content.clone() : null;
  }
}
