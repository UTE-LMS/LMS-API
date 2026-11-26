package com.hcmute.lms.lmsapi.exporter;

import com.hcmute.lms.lmsapi.exception.AppException;
import com.hcmute.lms.lmsapi.exception.ErrorCode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.http.HttpStatus;

/**
 * Tiện ích dùng chung cho xuất báo cáo Excel {@code .xlsx} (chuẩn hóa định dạng, chống công thức
 * CSV/Excel Injection CWE-1236, định dạng ô an toàn với mẫu số 0 và tính độ rộng cột không phụ
 * thuộc AWT font).
 */
public final class ExcelExportSupport {

  private static final int MIN_COL_WIDTH_CHARS = 10;
  private static final int MAX_COL_WIDTH_CHARS = 60;

  private ExcelExportSupport() {}

  /** Vai trò định dạng ô trong Workbook. */
  public enum StyleRole {
    TITLE,
    HEADER,
    META_LABEL,
    TEXT,
    INTEGER,
    DECIMAL,
    PERCENT
  }

  /**
   * Bộ style đóng gói nội bộ cho một {@link Workbook}, không làm rò rỉ tham chiếu {@link
   * CellStyle}.
   */
  public static final class WorkbookStyles {
    private final Map<StyleRole, CellStyle> byRole = new EnumMap<>(StyleRole.class);

    private WorkbookStyles(Workbook wb) {
      DataFormat dataFormat = wb.createDataFormat();

      CellStyle title = wb.createCellStyle();
      Font titleFont = wb.createFont();
      titleFont.setBold(true);
      titleFont.setFontHeightInPoints((short) 13);
      title.setFont(titleFont);
      byRole.put(StyleRole.TITLE, title);

      CellStyle header = wb.createCellStyle();
      Font headerFont = wb.createFont();
      headerFont.setBold(true);
      header.setFont(headerFont);
      header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
      header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
      header.setBorderTop(BorderStyle.THIN);
      header.setBorderBottom(BorderStyle.THIN);
      header.setBorderLeft(BorderStyle.THIN);
      header.setBorderRight(BorderStyle.THIN);
      byRole.put(StyleRole.HEADER, header);

      CellStyle metaLabel = wb.createCellStyle();
      Font metaFont = wb.createFont();
      metaFont.setBold(true);
      metaLabel.setFont(metaFont);
      byRole.put(StyleRole.META_LABEL, metaLabel);

      byRole.put(StyleRole.TEXT, wb.createCellStyle());

      CellStyle integer = wb.createCellStyle();
      integer.setDataFormat(dataFormat.getFormat("#,##0"));
      byRole.put(StyleRole.INTEGER, integer);

      CellStyle decimal = wb.createCellStyle();
      decimal.setDataFormat(dataFormat.getFormat("0.000"));
      byRole.put(StyleRole.DECIMAL, decimal);

      CellStyle percent = wb.createCellStyle();
      percent.setDataFormat(dataFormat.getFormat("0.00%"));
      byRole.put(StyleRole.PERCENT, percent);
    }

    private void apply(Cell cell, StyleRole role) {
      CellStyle style = role != null ? byRole.get(role) : null;
      if (style != null) {
        cell.setCellStyle(style);
      }
    }
  }

  /** Chuẩn hóa tham số {@code format} ({@code csv} mặc định hoặc {@code xlsx}). */
  public static String normalizeFormat(String format) {
    if (format == null || format.isBlank()) {
      return "csv";
    }
    String normalized = format.trim().toLowerCase(Locale.ROOT);
    if ("csv".equals(normalized) || "xlsx".equals(normalized)) {
      return normalized;
    }
    throw new AppException(
        ErrorCode.VALIDATION_ERROR,
        HttpStatus.BAD_REQUEST,
        "Định dạng xuất không hợp lệ: " + format + " (chỉ nhận csv hoặc xlsx)");
  }

  /**
   * Vô hiệu hóa chuỗi kích hoạt công thức bảng tính (CWE-1236) bằng tiền tố dấu nháy đơn {@code '}
   * khi giá trị gốc hoặc giá trị sau {@code trim()} bắt đầu bằng {@code =}, {@code +}, {@code -},
   * {@code @}, {@code \t}, {@code \r}.
   */
  public static String sanitizeFormulaText(String raw) {
    if (raw == null || raw.isEmpty()) {
      return "";
    }
    boolean startsWithFormulaChar = isFormulaTrigger(raw.charAt(0));
    if (!startsWithFormulaChar) {
      String trimmed = raw.trim();
      if (!trimmed.isEmpty() && isFormulaTrigger(trimmed.charAt(0))) {
        startsWithFormulaChar = true;
      }
    }
    return startsWithFormulaChar ? "'" + raw : raw;
  }

  private static boolean isFormulaTrigger(char c) {
    return c == '=' || c == '+' || c == '-' || c == '@' || c == '\t' || c == '\r';
  }

  /** Khởi tạo toàn bộ style dùng chung trên một {@link Workbook}. */
  public static WorkbookStyles createStyles(Workbook wb) {
    return new WorkbookStyles(wb);
  }

  /** Ghi ô văn bản thuần túy đã làm sạch công thức và cập nhật độ rộng cột. */
  public static void writeTextCell(
      Row row, int col, String value, WorkbookStyles styles, StyleRole role, int[] colWidths) {
    String safe = sanitizeFormulaText(value);
    Cell cell = row.createCell(col, CellType.STRING);
    cell.setCellValue(safe);
    if (styles != null) {
      styles.apply(cell, role);
    }
    trackWidth(colWidths, col, safe.length());
  }

  /** Ghi ô số nguyên; nếu {@code value == null} thì để trống ô (không ghi 0). */
  public static void writeIntCell(
      Row row, int col, Number value, WorkbookStyles styles, int[] colWidths) {
    if (value == null) {
      return;
    }
    Cell cell = row.createCell(col, CellType.NUMERIC);
    long num = value.longValue();
    cell.setCellValue((double) num);
    if (styles != null) {
      styles.apply(cell, StyleRole.INTEGER);
    }
    trackWidth(colWidths, col, String.valueOf(num).length());
  }

  /**
   * Ghi ô số thực; bảo toàn bất biến mẫu số bằng 0: nếu {@code value == null}, {@code NaN} hoặc
   * {@code Infinite} thì để trống ô (không bao giờ ghi {@code 0.0}).
   */
  public static void writeDoubleCell(
      Row row, int col, Double value, WorkbookStyles styles, StyleRole role, int[] colWidths) {
    if (value == null || Double.isNaN(value) || Double.isInfinite(value)) {
      return;
    }
    Cell cell = row.createCell(col, CellType.NUMERIC);
    cell.setCellValue(value);
    if (styles != null) {
      styles.apply(cell, role);
    }
    trackWidth(colWidths, col, 10);
  }

  /** Ghi một dòng metadata gồm nhãn đậm ở cột 0 và giá trị văn bản ở cột 1. */
  public static void writeMetadataRow(
      Sheet sheet, int rowIdx, String label, String value, WorkbookStyles styles, int[] colWidths) {
    Row row = sheet.createRow(rowIdx);
    writeTextCell(row, 0, label, styles, StyleRole.META_LABEL, colWidths);
    writeTextCell(row, 1, value, styles, StyleRole.TEXT, colWidths);
  }

  /**
   * Áp dụng độ rộng cột tính theo số ký tự. Tuyệt đối không dùng {@code sheet.autoSizeColumn()} để
   * tránh lỗi thiếu font AWT trên container Linux headless.
   */
  public static void applyColumnWidths(Sheet sheet, int[] colWidths) {
    if (colWidths == null) {
      return;
    }
    for (int c = 0; c < colWidths.length; c++) {
      int chars = Math.min(Math.max(colWidths[c], MIN_COL_WIDTH_CHARS), MAX_COL_WIDTH_CHARS);
      sheet.setColumnWidth(c, (chars + 2) * 256);
    }
  }

  /** Xuất {@link Workbook} ra mảng byte {@code .xlsx}. */
  public static byte[] toBytes(Workbook workbook) {
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      workbook.write(out);
      return out.toByteArray();
    } catch (IOException ex) {
      throw new AppException(
          ErrorCode.INTERNAL_ERROR,
          HttpStatus.INTERNAL_SERVER_ERROR,
          "Không tạo được tệp Excel .xlsx",
          ex);
    }
  }

  private static void trackWidth(int[] colWidths, int col, int length) {
    if (colWidths != null && col >= 0 && col < colWidths.length) {
      colWidths[col] = Math.max(colWidths[col], length);
    }
  }
}
