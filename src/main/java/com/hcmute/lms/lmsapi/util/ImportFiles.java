package com.hcmute.lms.lmsapi.util;

import com.hcmute.lms.lmsapi.exception.AppException;
import com.hcmute.lms.lmsapi.exception.ErrorCode;
import java.io.IOException;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

/**
 * Điểm vào chung của các chức năng nhập tệp: nhận {@code .csv} hoặc {@code .xlsx} và luôn trả về
 * nội dung CSV UTF-8 để các bộ đọc CSV hiện có xử lý (ADR 0015). Định dạng được xác định theo đuôi
 * tên tệp, rồi kiểm tra nội dung có đúng là tệp zip (.xlsx là gói zip) hay không.
 */
public final class ImportFiles {

  public static final String XLSX_EXTENSION = ".xlsx";
  public static final String CSV_EXTENSION = ".csv";

  /** Hai byte đầu của mọi tệp zip ("PK"), dấu hiệu tối thiểu để biết đúng là gói .xlsx. */
  private static final byte ZIP_MAGIC_0 = 'P';

  private static final byte ZIP_MAGIC_1 = 'K';

  private ImportFiles() {}

  /** Tên tệp có đuôi {@code .xlsx} (không phân biệt hoa thường). */
  public static boolean isXlsx(String fileName) {
    return hasExtension(fileName, XLSX_EXTENSION);
  }

  /**
   * Nội dung CSV UTF-8 của tệp tải lên. Tệp {@code .xlsx} được đổi sang CSV; mọi đuôi khác giữ
   * nguyên nội dung như trước đây, trừ {@code .xls} (Excel cũ) bị từ chối kèm hướng dẫn.
   */
  public static byte[] toCsvBytes(String fileName, byte[] content) {
    if (hasExtension(fileName, ".xls")) {
      throw invalid(
          "Chưa hỗ trợ tệp .xls (Excel cũ). Hãy mở bằng Excel và lưu thành .xlsx hoặc .csv");
    }
    if (!isXlsx(fileName)) {
      return content;
    }
    if (content == null
        || content.length < 2
        || content[0] != ZIP_MAGIC_0
        || content[1] != ZIP_MAGIC_1) {
      throw invalid("Tệp có đuôi .xlsx nhưng nội dung không phải tệp Excel .xlsx hợp lệ");
    }
    return XlsxCsvConverter.toCsv(content);
  }

  /** Như {@link #toCsvBytes(String, byte[])} cho tệp multipart. */
  public static byte[] toCsvBytes(MultipartFile file) {
    try {
      return toCsvBytes(file.getOriginalFilename(), file.getBytes());
    } catch (IOException ex) {
      throw invalid("Không đọc được nội dung tệp tải lên");
    }
  }

  private static boolean hasExtension(String fileName, String extension) {
    return fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(extension);
  }

  private static AppException invalid(String message) {
    return new AppException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, message);
  }
}
