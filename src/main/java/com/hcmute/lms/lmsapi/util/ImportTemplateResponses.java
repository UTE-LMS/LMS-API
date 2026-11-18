package com.hcmute.lms.lmsapi.util;

import com.hcmute.lms.lmsapi.dto.AssessmentScoreTemplate;
import com.hcmute.lms.lmsapi.exception.AppException;
import com.hcmute.lms.lmsapi.exception.ErrorCode;
import java.util.Locale;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Tạo phản hồi tải tệp mẫu nhập liệu. Các service sinh sẵn mẫu CSV; nếu người dùng chọn {@code
 * format=xlsx} thì mẫu đó được đổi sang Excel (ADR 0015) để mọi chức năng nhập dùng chung một chỗ
 * xử lý định dạng.
 */
public final class ImportTemplateResponses {

  public static final String CSV_MEDIA_TYPE = "text/csv; charset=UTF-8";
  public static final String XLSX_MEDIA_TYPE =
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

  private ImportTemplateResponses() {}

  /**
   * @param template mẫu CSV do service sinh
   * @param format {@code csv} (mặc định) hoặc {@code xlsx}, không phân biệt hoa thường
   */
  public static ResponseEntity<byte[]> of(AssessmentScoreTemplate template, String format) {
    String normalized = format == null ? "csv" : format.trim().toLowerCase(Locale.ROOT);
    String fileName = template.fileName();
    byte[] content = template.content();
    String mediaType;
    if ("xlsx".equals(normalized)) {
      content = XlsxCsvConverter.fromCsv(content);
      fileName = withExtension(fileName, ImportFiles.XLSX_EXTENSION);
      mediaType = XLSX_MEDIA_TYPE;
    } else if ("csv".equals(normalized)) {
      mediaType = CSV_MEDIA_TYPE;
    } else {
      throw new AppException(
          ErrorCode.VALIDATION_ERROR,
          HttpStatus.BAD_REQUEST,
          "Định dạng tệp mẫu không hợp lệ: " + format + " (chỉ nhận csv hoặc xlsx)");
    }
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
        .contentType(MediaType.parseMediaType(mediaType))
        .body(content);
  }

  private static String withExtension(String fileName, String extension) {
    int dot = fileName.lastIndexOf('.');
    return (dot > 0 ? fileName.substring(0, dot) : fileName) + extension;
  }
}
