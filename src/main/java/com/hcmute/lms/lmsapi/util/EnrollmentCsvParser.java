package com.hcmute.lms.lmsapi.util;

import com.hcmute.lms.lmsapi.exception.AppException;
import com.hcmute.lms.lmsapi.exception.ErrorCode;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;

/**
 * Đọc tệp CSV danh sách lớp (US-20). Chỉ nhận ba cột: {@code studentRef} (bắt buộc), {@code
 * cohort}, {@code majorCode}. Cột khác (ví dụ họ tên, email) bị bỏ qua và được báo lại, vì dữ liệu
 * cá nhân chỉ lưu khi được phép (Q09) và F05a chưa cần đến.
 *
 * <p>Định dạng: UTF-8 (chấp nhận BOM), dòng đầu là tiêu đề, phân cách bằng dấu phẩy hoặc chấm phẩy
 * (Excel tiếng Việt xuất chấm phẩy), hỗ trợ giá trị trong dấu nháy kép nhưng không hỗ trợ giá trị
 * xuống dòng. Chỉ đọc cú pháp; quy tắc nghiệp vụ (trùng, đã ghi danh...) do service kiểm tra.
 */
public final class EnrollmentCsvParser {

  /** Số dòng dữ liệu tối đa trong một tệp, để một lần nhập không chiếm quá nhiều tài nguyên. */
  public static final int MAX_DATA_ROWS = 5000;

  private static final String COLUMN_STUDENT_REF = "studentref";
  private static final String COLUMN_COHORT = "cohort";
  private static final String COLUMN_MAJOR_CODE = "majorcode";
  private static final char BOM = '﻿';

  /** Một dòng dữ liệu; {@code lineNumber} là số dòng trong tệp (dòng tiêu đề là dòng 1). */
  public record Row(int lineNumber, String studentRef, String cohort, String majorCode) {}

  /** Kết quả đọc: các dòng dữ liệu và tên các cột bị bỏ qua. */
  public record Result(List<Row> rows, List<String> ignoredColumns) {

    /** Sao chép bất biến để người gọi không sửa được dữ liệu bên trong. */
    public Result {
      rows = List.copyOf(rows);
      ignoredColumns = List.copyOf(ignoredColumns);
    }

    @Override
    public List<Row> rows() {
      return List.copyOf(rows);
    }

    @Override
    public List<String> ignoredColumns() {
      return List.copyOf(ignoredColumns);
    }
  }

  private EnrollmentCsvParser() {}

  public static Result parse(InputStream input) {
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
      String headerLine = reader.readLine();
      if (headerLine == null || headerLine.isBlank()) {
        throw invalid("Tệp rỗng hoặc thiếu dòng tiêu đề");
      }
      headerLine = stripBom(headerLine);

      char delimiter = detectDelimiter(headerLine);
      List<String> headers = splitLine(headerLine, delimiter);
      Map<String, Integer> columnIndex = new HashMap<>();
      Set<String> ignored = new LinkedHashSet<>();
      for (int i = 0; i < headers.size(); i++) {
        String name = headers.get(i).trim();
        String key = name.toLowerCase(Locale.ROOT);
        if (isKnownColumn(key)) {
          if (columnIndex.put(key, i) != null) {
            throw invalid("Cột [" + name + "] xuất hiện nhiều lần ở dòng tiêu đề");
          }
        } else if (!name.isEmpty()) {
          ignored.add(name);
        }
      }
      if (!columnIndex.containsKey(COLUMN_STUDENT_REF)) {
        throw invalid("Dòng tiêu đề phải có cột studentRef");
      }

      List<Row> rows = new ArrayList<>();
      int lineNumber = 1;
      String line;
      while ((line = reader.readLine()) != null) {
        lineNumber++;
        if (line.isBlank()) {
          continue;
        }
        if (rows.size() >= MAX_DATA_ROWS) {
          throw invalid("Tệp vượt quá " + MAX_DATA_ROWS + " dòng dữ liệu cho một lần nhập");
        }
        List<String> cells = splitLine(line, delimiter);
        rows.add(
            new Row(
                lineNumber,
                cell(cells, columnIndex.get(COLUMN_STUDENT_REF)),
                cell(cells, columnIndex.get(COLUMN_COHORT)),
                cell(cells, columnIndex.get(COLUMN_MAJOR_CODE))));
      }
      if (rows.isEmpty()) {
        throw invalid("Tệp không có dòng dữ liệu nào");
      }
      return new Result(rows, new ArrayList<>(ignored));
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  /** Bỏ ký tự BOM ở đầu dòng tiêu đề nếu có. Dùng chung với {@link ScoreCsvParser} (F05b). */
  static String stripBom(String headerLine) {
    return headerLine.charAt(0) == BOM ? headerLine.substring(1) : headerLine;
  }

  /** Dấu phân cách theo dòng tiêu đề: chấm phẩy chỉ khi có và không có dấu phẩy. */
  static char detectDelimiter(String headerLine) {
    return headerLine.contains(",") || !headerLine.contains(";") ? ',' : ';';
  }

  private static boolean isKnownColumn(String key) {
    return COLUMN_STUDENT_REF.equals(key)
        || COLUMN_COHORT.equals(key)
        || COLUMN_MAJOR_CODE.equals(key);
  }

  /** Giá trị ô đã bỏ khoảng trắng; cột không có trong tệp hoặc ô thiếu thì là chuỗi rỗng. */
  private static String cell(List<String> cells, Integer index) {
    if (index == null || index >= cells.size()) {
      return "";
    }
    return cells.get(index).trim();
  }

  /** Tách một dòng theo dấu phân cách, hỗ trợ nháy kép và nháy kép đôi ("") bên trong giá trị. */
  static List<String> splitLine(String line, char delimiter) {
    List<String> cells = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    boolean inQuotes = false;
    for (int i = 0; i < line.length(); i++) {
      char ch = line.charAt(i);
      if (inQuotes) {
        if (ch == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
          current.append('"');
          i++;
        } else if (ch == '"') {
          inQuotes = false;
        } else {
          current.append(ch);
        }
      } else if (ch == '"') {
        inQuotes = true;
      } else if (ch == delimiter) {
        cells.add(current.toString());
        current.setLength(0);
      } else {
        current.append(ch);
      }
    }
    cells.add(current.toString());
    return cells;
  }

  private static AppException invalid(String message) {
    return new AppException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, message);
  }
}
