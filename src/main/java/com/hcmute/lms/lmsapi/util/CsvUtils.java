package com.hcmute.lms.lmsapi.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Tiện ích đọc và phân tích tệp CSV thuần Java tuân thủ đặc tả RFC 4180.
 *
 * <p>Hỗ trợ dấu ngoặc kép ("..."), dấu phẩy trong chuỗi, escape quotes (""), xuống dòng CRLF/LF và
 * UTF-8 BOM.
 */
public final class CsvUtils {

  private CsvUtils() {}

  public static List<List<String>> parse(InputStream inputStream) throws IOException {
    return parse(inputStream, StandardCharsets.UTF_8);
  }

  public static List<List<String>> parse(InputStream inputStream, Charset charset)
      throws IOException {
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, charset))) {
      return parse(reader);
    }
  }

  public static List<List<String>> parse(BufferedReader reader) throws IOException {
    List<List<String>> records = new ArrayList<>();
    List<String> currentRecord = new ArrayList<>();
    StringBuilder currentField = new StringBuilder();
    boolean inQuotes = false;
    boolean startOfStream = true;
    int ch;

    while ((ch = reader.read()) != -1) {
      if (startOfStream) {
        startOfStream = false;
        if (ch == '\uFEFF') {
          // Bỏ qua ký tự UTF-8 BOM ở đầu tệp nếu có
          continue;
        }
      }

      if (inQuotes) {
        if (ch == '"') {
          reader.mark(1);
          int next = reader.read();
          if (next == '"') {
            // Dấu nháy kép escaped ("") bên trong trường
            currentField.append('"');
          } else {
            // Kết thúc trường trong dấu ngoặc kép
            inQuotes = false;
            if (next != -1) {
              reader.reset();
            }
          }
        } else {
          currentField.append((char) ch);
        }
      } else {
        if (ch == '"') {
          inQuotes = true;
        } else if (ch == ',') {
          currentRecord.add(currentField.toString().trim());
          currentField.setLength(0);
        } else if (ch == '\r') {
          reader.mark(1);
          int next = reader.read();
          if (next != '\n' && next != -1) {
            reader.reset();
          }
          currentRecord.add(currentField.toString().trim());
          currentField.setLength(0);
          if (!isRecordEmpty(currentRecord)) {
            records.add(new ArrayList<>(currentRecord));
          }
          currentRecord.clear();
        } else if (ch == '\n') {
          currentRecord.add(currentField.toString().trim());
          currentField.setLength(0);
          if (!isRecordEmpty(currentRecord)) {
            records.add(new ArrayList<>(currentRecord));
          }
          currentRecord.clear();
        } else {
          currentField.append((char) ch);
        }
      }
    }

    if (inQuotes) {
      throw new IllegalArgumentException(
          "Dữ liệu CSV không hợp lệ: dấu ngoặc kép không được đóng đúng quy cách");
    }

    // Xử lý bản ghi cuối cùng nếu tệp không kết thúc bằng dấu xuống dòng
    if (currentField.length() > 0 || !currentRecord.isEmpty()) {
      currentRecord.add(currentField.toString().trim());
      if (!isRecordEmpty(currentRecord)) {
        records.add(new ArrayList<>(currentRecord));
      }
    }

    return records;
  }

  private static boolean isRecordEmpty(List<String> record) {
    if (record.isEmpty()) {
      return true;
    }
    for (String field : record) {
      if (!field.isEmpty()) {
        return false;
      }
    }
    return true;
  }
}
