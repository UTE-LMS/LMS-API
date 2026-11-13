package com.hcmute.lms.lmsapi.service.impl;

import com.hcmute.lms.lmsapi.exception.BusinessRuleViolationException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.web.multipart.MultipartFile;

/**
 * Bộ đọc tệp CSV dùng chung cho Import CTĐT và Import Đề cương (UTF-8 / BOM, hỗ trợ dấu ngoặc kép).
 */
final class CsvTextParser {

  private CsvTextParser() {}

  static ParsedCsv read(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new BusinessRuleViolationException("Tệp CSV không được để trống");
    }
    List<String> rawLines = new ArrayList<>();
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (!line.trim().isEmpty()) {
          rawLines.add(line);
        }
      }
    } catch (IOException ex) {
      throw new BusinessRuleViolationException("Không đọc được nội dung tệp CSV");
    }
    if (rawLines.size() < 2) {
      throw new BusinessRuleViolationException(
          "Tệp CSV phải có dòng tiêu đề và ít nhất 1 dòng dữ liệu");
    }
    String headerLine = rawLines.get(0).replace("\uFEFF", "");
    List<String> headers = parseLine(headerLine);
    Map<String, Integer> headerIndex = new HashMap<>();
    for (int i = 0; i < headers.size(); i++) {
      headerIndex.put(headers.get(i).trim().toLowerCase(Locale.ROOT), i);
    }
    List<List<String>> rows = new ArrayList<>();
    for (int i = 1; i < rawLines.size(); i++) {
      rows.add(parseLine(rawLines.get(i)));
    }
    return new ParsedCsv(headerIndex, rows);
  }

  static void requireColumns(Map<String, Integer> headerIndex, String... requiredCols) {
    for (String col : requiredCols) {
      if (!headerIndex.containsKey(col.toLowerCase(Locale.ROOT))) {
        throw new BusinessRuleViolationException("Thiếu cột bắt buộc trong tệp CSV: " + col);
      }
    }
  }

  static String cell(Map<String, Integer> headerIndex, List<String> row, String col) {
    Integer idx = headerIndex.get(col.toLowerCase(Locale.ROOT));
    if (idx == null || idx >= row.size()) {
      return "";
    }
    return row.get(idx).trim();
  }

  static double parseUnitDouble(String raw, String fieldLabel, int rowNum) {
    if (raw.isEmpty()) {
      throw new BusinessRuleViolationException(
          "Dòng " + rowNum + ": thiếu giá trị bắt buộc cho " + fieldLabel);
    }
    try {
      double value = Double.parseDouble(raw);
      if (value <= 0.0 || value > 1.0) {
        throw new BusinessRuleViolationException(
            "Dòng " + rowNum + ": " + fieldLabel + " phải nằm trong (0, 1], nhận được " + raw);
      }
      return value;
    } catch (NumberFormatException ex) {
      throw new BusinessRuleViolationException(
          "Dòng " + rowNum + ": giá trị số không hợp lệ cho " + fieldLabel + ": " + raw);
    }
  }

  static int parseIntOrDefault(String raw, int defaultVal) {
    if (raw.isEmpty()) {
      return defaultVal;
    }
    try {
      return Integer.parseInt(raw);
    } catch (NumberFormatException ex) {
      return defaultVal;
    }
  }

  private static List<String> parseLine(String line) {
    List<String> fields = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    boolean inQuotes = false;
    for (int i = 0; i < line.length(); i++) {
      char ch = line.charAt(i);
      if (ch == '"') {
        if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
          current.append('"');
          i++;
        } else {
          inQuotes = !inQuotes;
        }
      } else if (ch == ',' && !inQuotes) {
        fields.add(current.toString());
        current.setLength(0);
      } else {
        current.append(ch);
      }
    }
    fields.add(current.toString());
    return fields;
  }

  record ParsedCsv(Map<String, Integer> headerIndex, List<List<String>> rows) {}
}
