package com.hcmute.lms.lmsapi.dto;

import com.hcmute.lms.lmsapi.enums.EmptyCellAs;
import com.hcmute.lms.lmsapi.enums.ImportSourceSystem;
import com.hcmute.lms.lmsapi.enums.ReimportPolicy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tham số tạo lô nhập (US-21, US-23), nhận từ các phần form của yêu cầu multipart cùng tệp. {@code
 * emptyCellAs} và {@code reimportPolicy} bắt buộc, không có mặc định: ô trống là BLANK hay MISSING
 * do người nhập chọn (US-23/AC-1); chính sách nhập lại chưa có mặc định (Q19).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportBatchCreateRequest {

  /** Mặc định CSV khi không gửi. */
  private ImportSourceSystem sourceSystem;

  private EmptyCellAs emptyCellAs;

  private ReimportPolicy reimportPolicy;

  /** Tùy chọn: bỏ trống thì dùng ánh xạ của lô gần nhất của cùng bài. */
  private ImportColumnMappingDto columnMapping;
}
