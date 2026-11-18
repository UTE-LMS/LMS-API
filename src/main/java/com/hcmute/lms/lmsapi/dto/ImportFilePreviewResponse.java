package com.hcmute.lms.lmsapi.dto;

import java.util.List;

/**
 * Kết quả xem trước tệp điểm (.csv hoặc .xlsx): danh sách tên cột và số dòng dữ liệu. Giao diện
 * dùng để tự ánh xạ cột khi trình duyệt không tự đọc được tệp (ADR 0015).
 */
public record ImportFilePreviewResponse(List<String> headers, int rowCount) {

  public ImportFilePreviewResponse {
    headers = List.copyOf(headers);
  }

  @Override
  public List<String> headers() {
    return List.copyOf(headers);
  }
}
