package com.hcmute.lms.lmsapi.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Băm SHA-256 của tệp (import_batches.fileChecksum), dùng chặn nhập trùng và kiểm tra toàn vẹn. */
public final class ChecksumUtils {

  private ChecksumUtils() {}

  /** SHA-256 của {@code content} dạng 64 ký tự hex thường. */
  public static String sha256Hex(byte[] content) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    } catch (NoSuchAlgorithmException ex) {
      // SHA-256 là thuật toán bắt buộc của mọi JVM, nên không thể xảy ra.
      throw new IllegalStateException("JVM không hỗ trợ SHA-256", ex);
    }
  }
}
