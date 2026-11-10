package com.hcmute.lms.lmsapi.util;

import com.hcmute.lms.lmsapi.exception.AppException;
import com.hcmute.lms.lmsapi.exception.ErrorCode;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;

/**
 * Đọc tệp CSV điểm (US-21, US-22) thành bảng: dòng tiêu đề và các dòng dữ liệu kèm số dòng vật lý
 * trong tệp (dòng tiêu đề là dòng 1, giống F05a). Chỉ kiểm tra cấu trúc cấp tệp (tệp rỗng, không có
 * dòng dữ liệu, trùng tiêu đề, quá nhiều dòng); mọi quy tắc theo dòng và theo cột do service kiểm
 * tra. Dùng lại cách tách dòng, dấu phân cách và BOM của {@link EnrollmentCsvParser}.
 */
public final class ScoreCsvParser {

  /** Một dòng dữ liệu; {@code cells} đã bỏ khoảng trắng hai đầu, số ô có thể khác số tiêu đề. */
  public record Row(int lineNumber, List<String> cells) {

    public Row {
      cells = List.copyOf(cells);
    }

    @Override
    public List<String> cells() {
      return List.copyOf(cells);
    }
  }

  /** Bảng đã đọc: tiêu đề và các dòng dữ liệu (không rỗng). */
  public record Table(List<String> headers, List<Row> rows) {

    public Table {
      headers = List.copyOf(headers);
      rows = List.copyOf(rows);
    }

    @Override
    public List<String> headers() {
      return List.copyOf(headers);
    }

    @Override
    public List<Row> rows() {
      return List.copyOf(rows);
    }

    /** Vị trí cột theo tên (không phân biệt hoa thường), -1 nếu không có. */
    public int indexOf(String column) {
      if (column == null) {
        return -1;
      }
      String key = column.trim().toLowerCase(Locale.ROOT);
      for (int i = 0; i < headers.size(); i++) {
        if (headers.get(i).toLowerCase(Locale.ROOT).equals(key)) {
          return i;
        }
      }
      return -1;
    }
  }

  private ScoreCsvParser() {}

  public static Table parse(byte[] content) {
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(new ByteArrayInputStream(content), StandardCharsets.UTF_8))) {
      String headerLine = reader.readLine();
      if (headerLine == null || headerLine.isBlank()) {
        throw invalid("Tệp rỗng hoặc thiếu dòng tiêu đề");
      }
      headerLine = EnrollmentCsvParser.stripBom(headerLine);
      char delimiter = EnrollmentCsvParser.detectDelimiter(headerLine);
      List<String> headers = trimAll(EnrollmentCsvParser.splitLine(headerLine, delimiter));
      requireDistinctHeaders(headers);

      List<Row> rows = new ArrayList<>();
      int lineNumber = 1;
      String line;
      while ((line = reader.readLine()) != null) {
        lineNumber++;
        if (line.isBlank()) {
          continue;
        }
        if (rows.size() >= EnrollmentCsvParser.MAX_DATA_ROWS) {
          throw invalid(
              "Tệp vượt quá "
                  + EnrollmentCsvParser.MAX_DATA_ROWS
                  + " dòng dữ liệu cho một lần nhập");
        }
        rows.add(new Row(lineNumber, trimAll(EnrollmentCsvParser.splitLine(line, delimiter))));
      }
      if (rows.isEmpty()) {
        throw invalid("Tệp không có dòng dữ liệu nào");
      }
      return new Table(headers, rows);
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  private static List<String> trimAll(List<String> cells) {
    return cells.stream().map(String::trim).toList();
  }

  private static void requireDistinctHeaders(List<String> headers) {
    Set<String> seen = new HashSet<>();
    for (String header : headers) {
      if (!header.isEmpty() && !seen.add(header.toLowerCase(Locale.ROOT))) {
        throw invalid("Cột [" + header + "] xuất hiện nhiều lần ở dòng tiêu đề");
      }
    }
  }

  private static AppException invalid(String message) {
    return new AppException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, message);
  }
}
