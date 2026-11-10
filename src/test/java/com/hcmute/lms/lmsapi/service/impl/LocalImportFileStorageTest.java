package com.hcmute.lms.lmsapi.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hcmute.lms.lmsapi.exception.AppException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("US-21: LocalImportFileStorage lưu tệp điểm gốc (ADR 0013)")
class LocalImportFileStorageTest {

  @TempDir Path tempDir;

  @Test
  @DisplayName("US-21/AC-1: lưu rồi đọc lại đúng từng byte, fileRef là <batchId>.csv")
  void saveThenLoadRoundTrips() {
    LocalImportFileStorage storage = new LocalImportFileStorage(tempDir.resolve("a/b").toString());
    String batchId = new ObjectId().toHexString();
    byte[] content = {'s', 't', 0, (byte) 0xC3, (byte) 0xA9};

    String fileRef = storage.save(batchId, content);

    assertThat(fileRef).isEqualTo(batchId + ".csv");
    assertThat(storage.load(fileRef)).containsExactly(content);
  }

  @Test
  @DisplayName("US-21: xóa tệp, xóa lại tệp đã mất không lỗi, đọc tệp đã mất báo lỗi 500")
  void deleteIsIdempotentAndLoadOfMissingFails() {
    LocalImportFileStorage storage = new LocalImportFileStorage(tempDir.toString());
    String fileRef = storage.save(new ObjectId().toHexString(), new byte[] {1});

    storage.delete(fileRef);
    storage.delete(fileRef);

    assertThat(Files.exists(tempDir.resolve(fileRef))).isFalse();
    assertThatThrownBy(() -> storage.load(fileRef))
        .isInstanceOfSatisfying(
            AppException.class, ex -> assertThat(ex.getHttpStatus().value()).isEqualTo(500));
  }

  @Test
  @DisplayName(
      "US-21 (bảo mật): fileRef không đúng mẫu <24 hex>.csv bị từ chối, không thoát thư mục")
  void invalidReferencesAreRejected() {
    LocalImportFileStorage storage = new LocalImportFileStorage(tempDir.toString());

    for (String bad : new String[] {"../x.csv", "abc.csv", "..\\..\\evil.csv", null, ""}) {
      assertThatThrownBy(() -> storage.load(bad)).isInstanceOf(AppException.class);
      assertThatThrownBy(() -> storage.delete(bad)).isInstanceOf(AppException.class);
    }
    assertThatThrownBy(() -> storage.save("../../evil", new byte[0]))
        .isInstanceOf(AppException.class);
  }

  @Test
  @DisplayName(
      "US-21: không tạo được thư mục (đường dẫn là một tệp) thì báo lỗi 500, không ghi nửa chừng")
  void saveFailsWhenDirectoryCannotBeCreated() throws Exception {
    Path blocker = Files.writeString(tempDir.resolve("blocker"), "x");
    LocalImportFileStorage storage = new LocalImportFileStorage(blocker.toString());

    assertThatThrownBy(() -> storage.save(new ObjectId().toHexString(), new byte[] {1}))
        .isInstanceOf(AppException.class);
  }
}
