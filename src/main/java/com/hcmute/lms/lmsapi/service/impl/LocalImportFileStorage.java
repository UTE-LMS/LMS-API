package com.hcmute.lms.lmsapi.service.impl;

import com.hcmute.lms.lmsapi.exception.AppException;
import com.hcmute.lms.lmsapi.exception.ErrorCode;
import com.hcmute.lms.lmsapi.service.ImportFileStorage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Lưu tệp điểm gốc vào một thư mục trên máy chủ (ADR 0013). Tên tệp là {@code <batchId>.csv} do máy
 * chủ đặt, và {@code fileRef} được kiểm tra đúng mẫu đó trước khi đọc/xóa, nên người dùng không thể
 * dẫn tới đường dẫn khác (path traversal).
 */
@Slf4j
@Component
public class LocalImportFileStorage implements ImportFileStorage {

  private static final Pattern FILE_REF = Pattern.compile("^[0-9a-f]{24}\\.csv$");

  private final Path directory;

  public LocalImportFileStorage(
      @Value("${app.score-import.storage-dir:./data/score-import}") String storageDir) {
    this.directory = Path.of(storageDir).toAbsolutePath().normalize();
  }

  @Override
  public String save(String batchId, byte[] content) {
    String fileRef = batchId + ".csv";
    requireValidRef(fileRef);
    try {
      Files.createDirectories(directory);
      Files.write(directory.resolve(fileRef), content);
    } catch (IOException ex) {
      throw storageError("Không lưu được tệp điểm gốc", ex);
    }
    return fileRef;
  }

  @Override
  public byte[] load(String fileRef) {
    requireValidRef(fileRef);
    try {
      return Files.readAllBytes(directory.resolve(fileRef));
    } catch (IOException ex) {
      throw storageError("Không đọc được tệp điểm gốc đã lưu: " + fileRef, ex);
    }
  }

  @Override
  public void delete(String fileRef) {
    requireValidRef(fileRef);
    try {
      Files.deleteIfExists(directory.resolve(fileRef));
    } catch (IOException ex) {
      // Tệp mồ côi không ảnh hưởng dữ liệu điểm, chỉ ghi log để người vận hành dọn.
      log.warn("Không xóa được tệp điểm gốc [{}]: {}", fileRef, ex.getMessage());
    }
  }

  private void requireValidRef(String fileRef) {
    if (fileRef == null || !FILE_REF.matcher(fileRef).matches()) {
      throw new AppException(
          ErrorCode.INTERNAL_ERROR,
          HttpStatus.INTERNAL_SERVER_ERROR,
          "Tham chiếu tệp điểm gốc không hợp lệ");
    }
  }

  private AppException storageError(String message, IOException cause) {
    return new AppException(
        ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, message, cause);
  }
}
