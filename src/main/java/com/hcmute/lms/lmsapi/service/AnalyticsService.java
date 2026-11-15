package com.hcmute.lms.lmsapi.service;

import com.hcmute.lms.lmsapi.dto.UserPrincipal;
import com.hcmute.lms.lmsapi.dto.analytics.AnalyticsMatrixResponse;
import com.hcmute.lms.lmsapi.dto.analytics.AnalyticsQueryRequest;

/**
 * Dịch vụ báo cáo phân tích dữ liệu đa chiều (Milestone 1). Hỗ trợ truy vấn động trên 5 chiều, kiểm
 * soát phạm vi phân quyền, và xuất báo cáo ma trận chuẩn RFC 4180 CSV.
 */
public interface AnalyticsService {

  /**
   * Thực hiện truy vấn động và tổng hợp ma trận phân tích đa chiều.
   *
   * @param request tham số truy vấn lọc và cấu hình chiều
   * @param principal thông tin người dùng xác thực trong Security Context
   * @return kết quả ma trận phân tích đa chiều
   */
  AnalyticsMatrixResponse getAnalyticsMatrix(
      AnalyticsQueryRequest request, UserPrincipal principal);

  /**
   * Xuất kết quả ma trận phân tích đa chiều sang file CSV kèm UTF-8 BOM.
   *
   * @param request tham số truy vấn lọc và cấu hình chiều
   * @param principal thông tin người dùng xác thực trong Security Context
   * @return mảng byte file CSV
   */
  byte[] exportAnalyticsMatrixCsv(AnalyticsQueryRequest request, UserPrincipal principal);
}
