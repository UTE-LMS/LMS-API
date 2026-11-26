package com.hcmute.lms.lmsapi.exporter;

import com.hcmute.lms.lmsapi.document.Report;
import com.hcmute.lms.lmsapi.enums.ReportType;
import com.hcmute.lms.lmsapi.exporter.ExcelExportSupport.StyleRole;
import com.hcmute.lms.lmsapi.exporter.ExcelExportSupport.WorkbookStyles;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/** Trình xuất báo cáo chuẩn đầu ra ({@link Report}) sang định dạng Excel {@code .xlsx}. */
@Component
public class ReportExcelExporter {

  private static final int SUMMARY_COLUMNS = 12;

  /**
   * Chuyển đổi bản chụp {@link Report} sang mảng byte {@code .xlsx}.
   *
   * @param report báo cáo đã lưu trong cơ sở dữ liệu
   * @return mảng byte tệp Excel {@code .xlsx}
   */
  public byte[] exportToXlsx(Report report) {
    try (Workbook workbook = new XSSFWorkbook()) {
      WorkbookStyles styles = ExcelExportSupport.createStyles(workbook);
      Sheet sheet = workbook.createSheet("BaoCao");
      int[] colWidths = new int[SUMMARY_COLUMNS];

      int rowIdx = 0;
      Row titleRow = sheet.createRow(rowIdx++);
      ExcelExportSupport.writeTextCell(
          titleRow, 0, "BÁO CÁO ĐO LƯỜNG CHUẨN ĐẦU RA", styles, StyleRole.TITLE, colWidths);

      Map<String, Object> summary =
          report != null && report.getSummary() != null
              ? report.getSummary()
              : Collections.emptyMap();

      rowIdx = writeReportMetadata(sheet, rowIdx, report, summary, styles, colWidths);
      rowIdx++;

      if (report != null && report.getType() == ReportType.DATA_COMPLETENESS) {
        writeCompletenessSection(sheet, rowIdx, summary, styles, colWidths);
      } else {
        writeAggregateItemsSection(sheet, rowIdx, summary, styles, colWidths);
      }

      ExcelExportSupport.applyColumnWidths(sheet, colWidths);
      return ExcelExportSupport.toBytes(workbook);
    } catch (IOException ex) {
      throw new UncheckedIOException("Không đóng được Workbook khi xuất báo cáo Excel", ex);
    }
  }

  private int writeReportMetadata(
      Sheet sheet,
      int startRow,
      Report report,
      Map<String, Object> summary,
      WorkbookStyles styles,
      int[] colWidths) {
    int r = startRow;
    String id = report != null && report.getId() != null ? report.getId() : "";
    String type = report != null && report.getType() != null ? report.getType().name() : "";
    String title = report != null && report.getTitle() != null ? report.getTitle() : "";
    String runId = report != null && report.getRunId() != null ? report.getRunId() : "";
    String pvId =
        report != null && report.getProgramVersionId() != null ? report.getProgramVersionId() : "";
    String isOfficial = Boolean.TRUE.equals(summary.get("isOfficial")) ? "CÓ" : "KHÔNG";
    String isStale = Boolean.TRUE.equals(summary.get("isStale")) ? "CÓ" : "KHÔNG";
    String limitations =
        report != null && report.getLimitations() != null ? report.getLimitations() : "";
    String createdAt =
        report != null && report.getCreatedAt() != null ? report.getCreatedAt().toString() : "";

    ExcelExportSupport.writeMetadataRow(sheet, r++, "Mã báo cáo", id, styles, colWidths);
    ExcelExportSupport.writeMetadataRow(sheet, r++, "Loại báo cáo", type, styles, colWidths);
    ExcelExportSupport.writeMetadataRow(sheet, r++, "Tiêu đề", title, styles, colWidths);
    ExcelExportSupport.writeMetadataRow(
        sheet, r++, "Lượt tính toán (RunId)", runId, styles, colWidths);
    ExcelExportSupport.writeMetadataRow(sheet, r++, "Phiên bản CTĐT", pvId, styles, colWidths);
    ExcelExportSupport.writeMetadataRow(sheet, r++, "Chính thức", isOfficial, styles, colWidths);
    ExcelExportSupport.writeMetadataRow(sheet, r++, "Dữ liệu đã cũ", isStale, styles, colWidths);
    ExcelExportSupport.writeMetadataRow(
        sheet, r++, "Giới hạn kết luận", limitations, styles, colWidths);
    ExcelExportSupport.writeMetadataRow(sheet, r++, "Thời điểm tạo", createdAt, styles, colWidths);
    return r;
  }

  private void writeCompletenessSection(
      Sheet sheet,
      int startRow,
      Map<String, Object> summary,
      WorkbookStyles styles,
      int[] colWidths) {
    int r = startRow;

    writeStatRow(
        sheet, r++, "Tổng bản ghi", asNumber(summary.get("totalRecords")), styles, colWidths);
    writeStatRow(
        sheet, r++, "Đã tính được", asNumber(summary.get("computedRecords")), styles, colWidths);
    writeStatRow(
        sheet,
        r++,
        "Thiếu dữ liệu",
        asNumber(summary.get("insufficientRecords")),
        styles,
        colWidths);

    Row compRow = sheet.createRow(r++);
    ExcelExportSupport.writeTextCell(
        compRow, 0, "Độ đầy đủ dữ liệu", styles, StyleRole.META_LABEL, colWidths);
    ExcelExportSupport.writeDoubleCell(
        compRow,
        1,
        asDouble(summary.get("dataCompleteness")),
        styles,
        StyleRole.PERCENT,
        colWidths);

    r++;
    String[] headers = {
      "Mã SV", "Cấp độ", "Mã đối tượng (SubjectId)", "Mã lớp (SectionId)", "Trạng thái", "Lý do"
    };
    Row headerRow = sheet.createRow(r++);
    for (int c = 0; c < headers.length; c++) {
      ExcelExportSupport.writeTextCell(
          headerRow, c, headers[c], styles, StyleRole.HEADER, colWidths);
    }

    Object missingObj = summary.get("missingEntries");
    if (missingObj instanceof List<?> entries && !entries.isEmpty()) {
      for (Object entryObj : entries) {
        if (entryObj instanceof Map<?, ?> entry) {
          Row row = sheet.createRow(r++);
          ExcelExportSupport.writeTextCell(
              row, 0, asString(entry.get("studentCode")), styles, StyleRole.TEXT, colWidths);
          ExcelExportSupport.writeTextCell(
              row, 1, asString(entry.get("level")), styles, StyleRole.TEXT, colWidths);
          ExcelExportSupport.writeTextCell(
              row, 2, asString(entry.get("subjectId")), styles, StyleRole.TEXT, colWidths);
          ExcelExportSupport.writeTextCell(
              row, 3, asString(entry.get("sectionId")), styles, StyleRole.TEXT, colWidths);
          ExcelExportSupport.writeTextCell(
              row, 4, asString(entry.get("status")), styles, StyleRole.TEXT, colWidths);
          ExcelExportSupport.writeTextCell(
              row, 5, asString(entry.get("reason")), styles, StyleRole.TEXT, colWidths);
        }
      }
    } else {
      Row emptyRow = sheet.createRow(r);
      ExcelExportSupport.writeTextCell(
          emptyRow, 0, "Không có bản ghi thiếu dữ liệu.", styles, StyleRole.TEXT, colWidths);
    }
  }

  private void writeStatRow(
      Sheet sheet, int rowIdx, String label, Number value, WorkbookStyles styles, int[] colWidths) {
    Row row = sheet.createRow(rowIdx);
    ExcelExportSupport.writeTextCell(row, 0, label, styles, StyleRole.META_LABEL, colWidths);
    ExcelExportSupport.writeIntCell(row, 1, value, styles, colWidths);
  }

  private void writeAggregateItemsSection(
      Sheet sheet,
      int startRow,
      Map<String, Object> summary,
      WorkbookStyles styles,
      int[] colWidths) {
    int r = startRow;
    String[] headers = {
      "Cấp độ",
      "Mã chuẩn",
      "Ghi danh",
      "Đánh giá",
      "Đạt",
      "Chưa đạt",
      "Thiếu dữ liệu",
      "Điểm trung bình",
      "Tỷ lệ đạt (Evaluated)",
      "Tỷ lệ đạt (Enrolled)",
      "Ngưỡng đạt",
      "Độ đầy đủ dữ liệu"
    };
    Row headerRow = sheet.createRow(r++);
    for (int c = 0; c < headers.length; c++) {
      ExcelExportSupport.writeTextCell(
          headerRow, c, headers[c], styles, StyleRole.HEADER, colWidths);
    }

    Object itemsObj = summary.get("items");
    if (itemsObj instanceof List<?> items && !items.isEmpty()) {
      for (Object itemObj : items) {
        if (itemObj instanceof Map<?, ?> rowMap) {
          Row row = sheet.createRow(r++);
          ExcelExportSupport.writeTextCell(
              row, 0, asString(rowMap.get("level")), styles, StyleRole.TEXT, colWidths);
          ExcelExportSupport.writeTextCell(
              row, 1, asString(rowMap.get("subjectCode")), styles, StyleRole.TEXT, colWidths);
          ExcelExportSupport.writeIntCell(
              row, 2, asNumber(rowMap.get("enrolled")), styles, colWidths);
          ExcelExportSupport.writeIntCell(
              row, 3, asNumber(rowMap.get("evaluated")), styles, colWidths);
          ExcelExportSupport.writeIntCell(
              row, 4, asNumber(rowMap.get("passed")), styles, colWidths);
          ExcelExportSupport.writeIntCell(
              row, 5, asNumber(rowMap.get("failed")), styles, colWidths);
          ExcelExportSupport.writeIntCell(
              row, 6, asNumber(rowMap.get("insufficientData")), styles, colWidths);
          ExcelExportSupport.writeDoubleCell(
              row, 7, asDouble(rowMap.get("mean")), styles, StyleRole.DECIMAL, colWidths);
          ExcelExportSupport.writeDoubleCell(
              row, 8, asDouble(rowMap.get("passRate")), styles, StyleRole.PERCENT, colWidths);
          ExcelExportSupport.writeDoubleCell(
              row,
              9,
              asDouble(rowMap.get("passRateOfEnrolled")),
              styles,
              StyleRole.PERCENT,
              colWidths);
          ExcelExportSupport.writeDoubleCell(
              row, 10, asDouble(rowMap.get("passThreshold")), styles, StyleRole.PERCENT, colWidths);
          ExcelExportSupport.writeDoubleCell(
              row,
              11,
              asDouble(rowMap.get("dataCompleteness")),
              styles,
              StyleRole.PERCENT,
              colWidths);
        }
      }
    } else {
      Row emptyRow = sheet.createRow(r);
      ExcelExportSupport.writeTextCell(
          emptyRow, 0, "Không có dữ liệu tổng hợp.", styles, StyleRole.TEXT, colWidths);
    }
  }

  private String asString(Object value) {
    return value != null ? String.valueOf(value) : "";
  }

  private Number asNumber(Object value) {
    return value instanceof Number n ? n : null;
  }

  private Double asDouble(Object value) {
    return value instanceof Number n ? n.doubleValue() : null;
  }
}
