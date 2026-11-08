package com.hcmute.lms.lmsapi.exception;

import com.hcmute.lms.lmsapi.dto.FieldErrorDetail;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Ngoại lệ khi tệp nhập có dòng không hợp lệ (HTTP 422). Mang danh sách lỗi theo từng dòng để người
 * dùng sửa tệp rồi nhập lại; không có dòng nào được ghi (US-20/AC-2).
 */
@Getter
public class ImportValidationException extends AppException {

  private final List<FieldErrorDetail> rowErrors;

  public ImportValidationException(List<FieldErrorDetail> rowErrors) {
    super(
        ErrorCode.BUSINESS_RULE_VIOLATION,
        HttpStatus.UNPROCESSABLE_ENTITY,
        String.format(
            "Tệp có %d dòng không hợp lệ, chưa có dữ liệu nào được ghi", rowErrors.size()));
    this.rowErrors = new ArrayList<>(rowErrors);
  }
}
