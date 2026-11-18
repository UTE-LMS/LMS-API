package com.hcmute.lms.lmsapi.util;

import com.hcmute.lms.lmsapi.exception.AppException;
import com.hcmute.lms.lmsapi.exception.ErrorCode;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;

/**
 * Cầu nối giữa Excel {@code .xlsx} và CSV (ADR 0015). Các chức năng nhập đều đã có bộ đọc CSV riêng
 * (kiểm tra cột, số dòng, thông báo lỗi theo dòng); thay vì viết lại từng bộ đọc cho Excel, tệp
 * {@code .xlsx} được đổi sang CSV UTF-8 rồi đi qua đúng bộ đọc cũ, nên hai định dạng luôn cư xử
 * giống nhau.
 *
 * <p>Quy tắc đọc {@code .xlsx}:
 *
 * <ul>
 *   <li>Chỉ đọc trang tính đầu tiên; dòng 1 là tiêu đề.
 *   <li>Dòng thứ n của Excel thành dòng thứ n của CSV (dòng trống vẫn giữ chỗ), nên số dòng trong
 *       báo cáo lỗi khớp với số dòng người dùng thấy trong Excel.
 *   <li>Ô lấy theo giá trị hiển thị (số nguyên hiện là "2110001", không phải "2110001.0"), dấu thập
 *       phân luôn là dấu chấm. Ô công thức lấy giá trị đã tính lưu sẵn trong tệp, công thức không
 *       bao giờ được thực thi.
 *   <li>Xuống dòng trong ô bị đổi thành khoảng trắng vì các bộ đọc CSV xử lý theo từng dòng. Ô gộp
 *       chỉ có giá trị ở ô trên cùng bên trái.
 * </ul>
 */
public final class XlsxCsvConverter {

  /** Số dòng tối đa (kể cả tiêu đề) có dữ liệu trong trang tính, chặn tệp quá lớn. */
  public static final int MAX_ROWS = 20_000;

  /** Số cột tối đa có dữ liệu. */
  public static final int MAX_COLUMNS = 200;

  private static final int MAX_TEMPLATE_COLUMN_CHARS = 60;

  private XlsxCsvConverter() {}

  /** Đổi nội dung {@code .xlsx} sang CSV UTF-8 (phân cách bằng dấu phẩy, không BOM). */
  public static byte[] toCsv(byte[] xlsx) {
    try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
      if (workbook.getNumberOfSheets() == 0) {
        throw invalid("Tệp Excel không có trang tính nào");
      }
      return sheetToCsv(workbook.getSheetAt(0));
    } catch (IOException | RuntimeException ex) {
      if (ex instanceof AppException appException) {
        throw appException;
      }
      throw invalid(
          "Không đọc được tệp .xlsx (tệp hỏng, bị đặt mật khẩu hoặc không đúng định dạng Excel)");
    }
  }

  /**
   * Ghi bảng (dòng đầu là tiêu đề) thành tệp {@code .xlsx}, dùng để tải tệp mẫu. Mọi ô là văn bản
   * để mã sinh viên dạng "007" không bị Excel đổi thành số 7.
   */
  public static byte[] fromCsv(byte[] csv) {
    List<List<String>> rows;
    try {
      rows = CsvUtils.parse(new ByteArrayInputStream(csv));
    } catch (IOException | IllegalArgumentException ex) {
      throw new AppException(
          ErrorCode.INTERNAL_ERROR,
          HttpStatus.INTERNAL_SERVER_ERROR,
          "Không tạo được tệp mẫu .xlsx",
          ex);
    }
    try (Workbook workbook = new XSSFWorkbook();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      Sheet sheet = workbook.createSheet("Template");
      CellStyle headerStyle = workbook.createCellStyle();
      Font bold = workbook.createFont();
      bold.setBold(true);
      headerStyle.setFont(bold);

      int columns = 0;
      for (int r = 0; r < rows.size(); r++) {
        List<String> values = rows.get(r);
        columns = Math.max(columns, values.size());
        Row row = sheet.createRow(r);
        for (int c = 0; c < values.size(); c++) {
          Cell cell = row.createCell(c, CellType.STRING);
          cell.setCellValue(values.get(c));
          if (r == 0) {
            cell.setCellStyle(headerStyle);
          }
        }
      }
      for (int c = 0; c < columns; c++) {
        sheet.setColumnWidth(c, (widestCell(rows, c) + 2) * 256);
      }
      workbook.write(out);
      return out.toByteArray();
    } catch (IOException ex) {
      throw new AppException(
          ErrorCode.INTERNAL_ERROR,
          HttpStatus.INTERNAL_SERVER_ERROR,
          "Không tạo được tệp mẫu .xlsx",
          ex);
    }
  }

  private static int widestCell(List<List<String>> rows, int column) {
    int widest = 8;
    for (List<String> row : rows) {
      if (column < row.size()) {
        widest = Math.max(widest, row.get(column).length());
      }
    }
    return Math.min(widest, MAX_TEMPLATE_COLUMN_CHARS);
  }

  private static byte[] sheetToCsv(Sheet sheet) {
    DataFormatter formatter = new DataFormatter(Locale.US);
    List<String> lines = new ArrayList<>();
    for (Row row : sheet) {
      String line = rowToCsvLine(row, formatter);
      if (line.isEmpty()) {
        continue;
      }
      int rowIndex = row.getRowNum();
      if (rowIndex >= MAX_ROWS) {
        throw invalid("Tệp Excel vượt quá " + MAX_ROWS + " dòng");
      }
      while (lines.size() < rowIndex) {
        lines.add("");
      }
      lines.add(line);
    }
    if (lines.isEmpty()) {
      throw invalid("Tệp Excel rỗng hoặc trang tính đầu tiên không có dữ liệu");
    }
    return (String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8);
  }

  /** Một dòng CSV; chuỗi rỗng khi cả dòng không có dữ liệu. */
  private static String rowToCsvLine(Row row, DataFormatter formatter) {
    int lastCell = row.getLastCellNum();
    if (lastCell <= 0) {
      return "";
    }
    List<String> values = new ArrayList<>();
    boolean hasData = false;
    for (int c = 0; c < lastCell; c++) {
      Cell cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
      String text = cell == null ? "" : cellText(cell, formatter).trim();
      if (!text.isEmpty()) {
        if (c >= MAX_COLUMNS) {
          throw invalid("Tệp Excel vượt quá " + MAX_COLUMNS + " cột");
        }
        hasData = true;
      }
      values.add(text);
    }
    if (!hasData) {
      return "";
    }
    return String.join(",", values.stream().map(XlsxCsvConverter::csvField).toList());
  }

  private static String cellText(Cell cell, DataFormatter formatter) {
    CellType type = cell.getCellType();
    if (type == CellType.FORMULA) {
      type = cell.getCachedFormulaResultType();
    }
    return switch (type) {
      case STRING -> cell.getStringCellValue();
      case NUMERIC -> {
        CellStyle style = cell.getCellStyle();
        yield formatter.formatRawCellContents(
            cell.getNumericCellValue(), style.getDataFormat(), style.getDataFormatString());
      }
      case BOOLEAN -> cell.getBooleanCellValue() ? "TRUE" : "FALSE";
      default -> "";
    };
  }

  private static String csvField(String value) {
    String flat = value.replace("\r\n", " ").replace('\r', ' ').replace('\n', ' ');
    if (flat.contains(",") || flat.contains("\"")) {
      return "\"" + flat.replace("\"", "\"\"") + "\"";
    }
    return flat;
  }

  private static AppException invalid(String message) {
    return new AppException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, message);
  }
}
