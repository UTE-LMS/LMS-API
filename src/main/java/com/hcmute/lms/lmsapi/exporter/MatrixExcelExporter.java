package com.hcmute.lms.lmsapi.exporter;

import com.hcmute.lms.lmsapi.dto.analytics.AnalyticsMatrixResponse;
import com.hcmute.lms.lmsapi.dto.analytics.MatrixHeader;
import com.hcmute.lms.lmsapi.dto.analytics.MatrixMetricCell;
import com.hcmute.lms.lmsapi.exporter.ExcelExportSupport.StyleRole;
import com.hcmute.lms.lmsapi.exporter.ExcelExportSupport.WorkbookStyles;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/**
 * Trình xuất dữ liệu ma trận phân tích đa chiều sang định dạng Excel {@code .xlsx} gồm 2 trang
 * tính: {@code MaTranPhanTich} (bảng cross-tab) và {@code DuLieuChiTiet} (bảng phẳng 10 chỉ số).
 * Bảo toàn quy tắc mẫu số bằng 0 (ô {@code null} được giữ trống, không bao giờ ép về {@code 0.0}).
 */
@Component
public class MatrixExcelExporter {

  private static final int DETAIL_COLUMNS = 12;

  /**
   * Chuyển đổi dữ liệu ma trận sang mảng byte Excel {@code .xlsx}.
   *
   * @param response dữ liệu ma trận phân tích
   * @return mảng byte tệp Excel {@code .xlsx}
   */
  public byte[] exportToXlsx(AnalyticsMatrixResponse response) {
    try (Workbook workbook = new XSSFWorkbook()) {
      WorkbookStyles styles = ExcelExportSupport.createStyles(workbook);
      buildMatrixSheet(workbook, response, styles);
      buildDetailSheet(workbook, response, styles);
      return ExcelExportSupport.toBytes(workbook);
    } catch (IOException ex) {
      throw new UncheckedIOException("Không đóng được Workbook khi xuất ma trận Excel", ex);
    }
  }

  private void buildMatrixSheet(
      Workbook workbook, AnalyticsMatrixResponse response, WorkbookStyles styles) {
    Sheet sheet = workbook.createSheet("MaTranPhanTich");
    List<MatrixHeader> columns = response != null ? response.getColumns() : null;
    List<MatrixHeader> rows = response != null ? response.getRows() : null;
    List<List<MatrixMetricCell>> matrix = response != null ? response.getMatrix() : null;
    int colCount = 3 + (columns != null ? columns.size() : 0);
    int[] colWidths = new int[Math.max(colCount, 4)];

    int r = 0;
    Row titleRow = sheet.createRow(r++);
    ExcelExportSupport.writeTextCell(
        titleRow,
        0,
        "BÁO CÁO PHÂN TÍCH ĐA CHIỀU (CROSS-TAB ANALYTICS)",
        styles,
        StyleRole.TITLE,
        colWidths);

    String dimension =
        response != null && response.getDimension() != null ? response.getDimension().name() : "";
    String level =
        response != null && response.getLevel() != null ? response.getLevel().name() : "";
    String runId = response != null && response.getRunId() != null ? response.getRunId() : "N/A";
    String isOfficial =
        response != null && Boolean.TRUE.equals(response.getIsOfficial()) ? "CÓ" : "KHÔNG";
    String isStale =
        response != null && Boolean.TRUE.equals(response.getIsStale()) ? "CÓ" : "KHÔNG";

    ExcelExportSupport.writeMetadataRow(
        sheet, r++, "Chiều phân tích", dimension, styles, colWidths);
    ExcelExportSupport.writeMetadataRow(sheet, r++, "Cấp độ", level, styles, colWidths);
    ExcelExportSupport.writeMetadataRow(
        sheet, r++, "Lượt tính toán (RunId)", runId, styles, colWidths);
    ExcelExportSupport.writeMetadataRow(sheet, r++, "Chính thức", isOfficial, styles, colWidths);
    ExcelExportSupport.writeMetadataRow(sheet, r++, "Dữ liệu đã cũ", isStale, styles, colWidths);
    ExcelExportSupport.writeMetadataRow(
        sheet, r++, "Thời điểm xuất", Instant.now().toString(), styles, colWidths);
    r++;

    if (columns == null || columns.isEmpty() || rows == null || rows.isEmpty() || matrix == null) {
      Row emptyRow = sheet.createRow(r);
      ExcelExportSupport.writeTextCell(
          emptyRow,
          0,
          "Không có dữ liệu phân tích phù hợp với điều kiện lọc.",
          styles,
          StyleRole.TEXT,
          colWidths);
      ExcelExportSupport.applyColumnWidths(sheet, colWidths);
      return;
    }

    Row headerRow = sheet.createRow(r++);
    ExcelExportSupport.writeTextCell(headerRow, 0, "Mã", styles, StyleRole.HEADER, colWidths);
    ExcelExportSupport.writeTextCell(headerRow, 1, "Tên", styles, StyleRole.HEADER, colWidths);
    ExcelExportSupport.writeTextCell(headerRow, 2, "Chỉ số", styles, StyleRole.HEADER, colWidths);
    for (int c = 0; c < columns.size(); c++) {
      MatrixHeader col = columns.get(c);
      String colLabel =
          col.getCode() != null ? col.getCode() : (col.getName() != null ? col.getName() : "");
      ExcelExportSupport.writeTextCell(
          headerRow, 3 + c, colLabel, styles, StyleRole.HEADER, colWidths);
    }

    for (int rowIdx = 0; rowIdx < rows.size(); rowIdx++) {
      MatrixHeader rowHeader = rows.get(rowIdx);
      String rowCode = rowHeader.getCode() != null ? rowHeader.getCode() : "";
      String rowName = rowHeader.getName() != null ? rowHeader.getName() : "";
      List<MatrixMetricCell> rowCells = rowIdx < matrix.size() ? matrix.get(rowIdx) : List.of();

      Row rPassRate =
          createMetricRow(sheet, r++, rowCode, rowName, "Tỷ lệ đạt (Evaluated)", styles, colWidths);
      Row rEnrolledRate =
          createMetricRow(sheet, r++, rowCode, rowName, "Tỷ lệ đạt (Enrolled)", styles, colWidths);
      Row rMean =
          createMetricRow(sheet, r++, rowCode, rowName, "Điểm trung bình", styles, colWidths);
      Row rCounts =
          createMetricRow(sheet, r++, rowCode, rowName, "Đánh giá / Ghi danh", styles, colWidths);

      for (int c = 0; c < columns.size(); c++) {
        MatrixMetricCell cell = (rowCells != null && c < rowCells.size()) ? rowCells.get(c) : null;
        int col = 3 + c;
        ExcelExportSupport.writeDoubleCell(
            rPassRate,
            col,
            cell != null ? cell.getPassRate() : null,
            styles,
            StyleRole.PERCENT,
            colWidths);
        ExcelExportSupport.writeDoubleCell(
            rEnrolledRate,
            col,
            cell != null ? cell.getPassRateOfEnrolled() : null,
            styles,
            StyleRole.PERCENT,
            colWidths);
        ExcelExportSupport.writeDoubleCell(
            rMean, col, cell != null ? cell.getMean() : null, styles, StyleRole.DECIMAL, colWidths);
        String countsText =
            cell != null
                ? (cell.getEvaluated() != null ? cell.getEvaluated() : 0)
                    + " / "
                    + (cell.getEnrolled() != null ? cell.getEnrolled() : 0)
                : "0 / 0";
        ExcelExportSupport.writeTextCell(
            rCounts, col, countsText, styles, StyleRole.TEXT, colWidths);
      }
    }

    ExcelExportSupport.applyColumnWidths(sheet, colWidths);
  }

  private Row createMetricRow(
      Sheet sheet,
      int rowIdx,
      String rowCode,
      String rowName,
      String metricLabel,
      WorkbookStyles styles,
      int[] colWidths) {
    Row row = sheet.createRow(rowIdx);
    ExcelExportSupport.writeTextCell(row, 0, rowCode, styles, StyleRole.TEXT, colWidths);
    ExcelExportSupport.writeTextCell(row, 1, rowName, styles, StyleRole.TEXT, colWidths);
    ExcelExportSupport.writeTextCell(row, 2, metricLabel, styles, StyleRole.TEXT, colWidths);
    return row;
  }

  private void buildDetailSheet(
      Workbook workbook, AnalyticsMatrixResponse response, WorkbookStyles styles) {
    Sheet sheet = workbook.createSheet("DuLieuChiTiet");
    int[] colWidths = new int[DETAIL_COLUMNS];
    String[] headers = {
      "RowCode",
      "ColCode",
      "Enrolled",
      "Evaluated",
      "Passed",
      "Failed",
      "InsufficientData",
      "Mean",
      "PassRate",
      "PassRateOfEnrolled",
      "IsOfficial",
      "IsStale"
    };

    int r = 0;
    Row headerRow = sheet.createRow(r++);
    for (int c = 0; c < headers.length; c++) {
      ExcelExportSupport.writeTextCell(
          headerRow, c, headers[c], styles, StyleRole.HEADER, colWidths);
    }

    List<MatrixHeader> columns = response != null ? response.getColumns() : null;
    List<MatrixHeader> rows = response != null ? response.getRows() : null;
    List<List<MatrixMetricCell>> matrix = response != null ? response.getMatrix() : null;

    if (columns != null
        && !columns.isEmpty()
        && rows != null
        && !rows.isEmpty()
        && matrix != null) {
      for (int rowIdx = 0; rowIdx < rows.size(); rowIdx++) {
        String rowCode = rows.get(rowIdx).getCode() != null ? rows.get(rowIdx).getCode() : "";
        List<MatrixMetricCell> rowCells = rowIdx < matrix.size() ? matrix.get(rowIdx) : List.of();
        for (int c = 0; c < columns.size(); c++) {
          String colCode = columns.get(c).getCode() != null ? columns.get(c).getCode() : "";
          MatrixMetricCell cell =
              (rowCells != null && c < rowCells.size()) ? rowCells.get(c) : null;

          Row row = sheet.createRow(r++);
          ExcelExportSupport.writeTextCell(row, 0, rowCode, styles, StyleRole.TEXT, colWidths);
          ExcelExportSupport.writeTextCell(row, 1, colCode, styles, StyleRole.TEXT, colWidths);
          ExcelExportSupport.writeIntCell(
              row,
              2,
              cell != null && cell.getEnrolled() != null ? cell.getEnrolled() : 0,
              styles,
              colWidths);
          ExcelExportSupport.writeIntCell(
              row,
              3,
              cell != null && cell.getEvaluated() != null ? cell.getEvaluated() : 0,
              styles,
              colWidths);
          ExcelExportSupport.writeIntCell(
              row,
              4,
              cell != null && cell.getPassed() != null ? cell.getPassed() : 0,
              styles,
              colWidths);
          ExcelExportSupport.writeIntCell(
              row,
              5,
              cell != null && cell.getFailed() != null ? cell.getFailed() : 0,
              styles,
              colWidths);
          ExcelExportSupport.writeIntCell(
              row,
              6,
              cell != null && cell.getInsufficientData() != null ? cell.getInsufficientData() : 0,
              styles,
              colWidths);
          ExcelExportSupport.writeDoubleCell(
              row, 7, cell != null ? cell.getMean() : null, styles, StyleRole.DECIMAL, colWidths);
          ExcelExportSupport.writeDoubleCell(
              row,
              8,
              cell != null ? cell.getPassRate() : null,
              styles,
              StyleRole.PERCENT,
              colWidths);
          ExcelExportSupport.writeDoubleCell(
              row,
              9,
              cell != null ? cell.getPassRateOfEnrolled() : null,
              styles,
              StyleRole.PERCENT,
              colWidths);
          ExcelExportSupport.writeTextCell(
              row,
              10,
              cell != null && Boolean.TRUE.equals(cell.getIsOfficial()) ? "true" : "false",
              styles,
              StyleRole.TEXT,
              colWidths);
          ExcelExportSupport.writeTextCell(
              row,
              11,
              cell != null && Boolean.TRUE.equals(cell.getIsStale()) ? "true" : "false",
              styles,
              StyleRole.TEXT,
              colWidths);
        }
      }
    }

    ExcelExportSupport.applyColumnWidths(sheet, colWidths);
  }
}
