package com.hcmute.lms.lmsapi.exporter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hcmute.lms.lmsapi.document.Report;
import com.hcmute.lms.lmsapi.dto.analytics.AnalyticsMatrixResponse;
import com.hcmute.lms.lmsapi.dto.analytics.MatrixHeader;
import com.hcmute.lms.lmsapi.dto.analytics.MatrixMetricCell;
import com.hcmute.lms.lmsapi.enums.AnalyticsDimension;
import com.hcmute.lms.lmsapi.enums.ReportType;
import com.hcmute.lms.lmsapi.enums.ResultLevel;
import com.hcmute.lms.lmsapi.exception.AppException;
import com.hcmute.lms.lmsapi.exception.ErrorCode;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ExcelExporters & ExcelExportSupport Unit Tests")
class ExcelExportersTest {

  private final ReportExcelExporter reportExcelExporter = new ReportExcelExporter();
  private final MatrixExcelExporter matrixExcelExporter = new MatrixExcelExporter();

  @Test
  @DisplayName("normalizeFormat chuẩn hóa csv/xlsx và từ chối định dạng khác (400)")
  void normalizeFormat_validAndInvalid() {
    assertThat(ExcelExportSupport.normalizeFormat(null)).isEqualTo("csv");
    assertThat(ExcelExportSupport.normalizeFormat("  ")).isEqualTo("csv");
    assertThat(ExcelExportSupport.normalizeFormat("CSV")).isEqualTo("csv");
    assertThat(ExcelExportSupport.normalizeFormat("xlsx")).isEqualTo("xlsx");
    assertThat(ExcelExportSupport.normalizeFormat(" XLSX ")).isEqualTo("xlsx");

    assertThatThrownBy(() -> ExcelExportSupport.normalizeFormat("pdf"))
        .isInstanceOf(AppException.class)
        .satisfies(
            ex ->
                assertThat(((AppException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.VALIDATION_ERROR));
  }

  @Test
  @DisplayName("ReportExcelExporter xuất báo cáo PROGRAM_PLO giữ ô trống khi mẫu số bằng 0")
  void reportExcelExporter_programPloAndNullSafety() throws Exception {
    Map<String, Object> itemWithData = new LinkedHashMap<>();
    itemWithData.put("level", "PLO");
    itemWithData.put("subjectCode", "PLO1");
    itemWithData.put("enrolled", 40);
    itemWithData.put("evaluated", 38);
    itemWithData.put("passed", 32);
    itemWithData.put("failed", 6);
    itemWithData.put("insufficientData", 2);
    itemWithData.put("mean", 0.785);
    itemWithData.put("passRate", 32.0 / 38.0);
    itemWithData.put("passRateOfEnrolled", 0.8);
    itemWithData.put("passThreshold", 0.6);
    itemWithData.put("dataCompleteness", 0.95);

    Map<String, Object> itemNoData = new LinkedHashMap<>();
    itemNoData.put("level", "PLO");
    itemNoData.put("subjectCode", "=PLO2");
    itemNoData.put("enrolled", 40);
    itemNoData.put("evaluated", 0);
    itemNoData.put("passed", 0);
    itemNoData.put("failed", 0);
    itemNoData.put("insufficientData", 40);
    itemNoData.put("mean", null);
    itemNoData.put("passRate", null);
    itemNoData.put("passRateOfEnrolled", null);
    itemNoData.put("passThreshold", 0.6);
    itemNoData.put("dataCompleteness", 0.0);

    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("isOfficial", true);
    summary.put("isStale", false);
    summary.put("items", List.of(itemWithData, itemNoData));

    Report report =
        Report.builder()
            .id("rep1")
            .type(ReportType.PROGRAM_PLO)
            .title("@BaoCaoPLO")
            .runId("run1")
            .programVersionId("pv1")
            .summary(summary)
            .limitations("Chưa đủ dữ liệu để kết luận toàn chương trình.")
            .createdAt(Instant.parse("2026-11-26T00:00:00Z"))
            .build();

    byte[] bytes = reportExcelExporter.exportToXlsx(report);

    try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
      Sheet sheet = wb.getSheet("BaoCao");
      assertThat(sheet).isNotNull();

      Row rowPlo1 = findRowByFirstCells(sheet, "PLO", "PLO1");
      assertThat(rowPlo1).isNotNull();
      assertThat(rowPlo1.getCell(2).getNumericCellValue()).isEqualTo(40.0);
      assertThat(rowPlo1.getCell(7).getNumericCellValue()).isEqualTo(0.785);
      assertThat(rowPlo1.getCell(9).getNumericCellValue()).isEqualTo(0.8);

      Row rowPlo2 = findRowByFirstCells(sheet, "PLO", "'=PLO2");
      assertThat(rowPlo2).isNotNull();
      assertThat(rowPlo2.getCell(1).getCellType()).isEqualTo(CellType.STRING);
      assertThat(rowPlo2.getCell(7)).isNull();
      assertThat(rowPlo2.getCell(8)).isNull();
      assertThat(rowPlo2.getCell(9)).isNull();

      Row titleMetaRow = findRowByFirstCells(sheet, "Tiêu đề", "'@BaoCaoPLO");
      assertThat(titleMetaRow).isNotNull();
      assertThat(titleMetaRow.getCell(1).getCellType()).isEqualTo(CellType.STRING);
    }
  }

  @Test
  @DisplayName("ReportExcelExporter xuất DATA_COMPLETENESS và danh sách rỗng an toàn")
  void reportExcelExporter_dataCompletenessAndEmpty() throws Exception {
    Map<String, Object> missingEntry = new LinkedHashMap<>();
    missingEntry.put("studentCode", "22110002");
    missingEntry.put("level", "CLO");
    missingEntry.put("subjectId", "clo1");
    missingEntry.put("sectionId", "sec1");
    missingEntry.put("status", "INSUFFICIENT_DATA");
    missingEntry.put("reason", "Thiếu điểm ở một hoặc nhiều bài đánh giá/CLO");

    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("isOfficial", false);
    summary.put("isStale", true);
    summary.put("totalRecords", 2L);
    summary.put("computedRecords", 1L);
    summary.put("insufficientRecords", 1);
    summary.put("dataCompleteness", 0.5);
    summary.put("missingEntries", List.of(missingEntry));

    Report report =
        Report.builder()
            .id("rep2")
            .type(ReportType.DATA_COMPLETENESS)
            .title("Báo cáo độ đầy đủ")
            .runId("run1")
            .summary(summary)
            .build();

    byte[] bytes = reportExcelExporter.exportToXlsx(report);
    try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
      Sheet sheet = wb.getSheet("BaoCao");
      assertThat(sheet).isNotNull();
      Row missingRow = findRowByFirstCells(sheet, "22110002", "CLO");
      assertThat(missingRow).isNotNull();
      assertThat(missingRow.getCell(4).getStringCellValue()).isEqualTo("INSUFFICIENT_DATA");
    }

    Report emptyReport =
        Report.builder()
            .id("rep3")
            .type(ReportType.SECTION_CLO)
            .title("Trống")
            .runId("run1")
            .summary(Map.of())
            .build();
    byte[] emptyBytes = reportExcelExporter.exportToXlsx(emptyReport);
    try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(emptyBytes))) {
      Sheet sheet = wb.getSheet("BaoCao");
      assertThat(findRowByFirstCell(sheet, "Không có dữ liệu tổng hợp.")).isNotNull();
    }
  }

  @Test
  @DisplayName(
      "MatrixExcelExporter xuất 2 sheet MaTranPhanTich & DuLieuChiTiet, giữ null và chống injection")
  void matrixExcelExporter_twoSheetsAndNullSafety() throws Exception {
    AnalyticsMatrixResponse response =
        AnalyticsMatrixResponse.builder()
            .dimension(AnalyticsDimension.COURSE_SECTIONS)
            .level(ResultLevel.CLO)
            .runId("run_1")
            .isOfficial(true)
            .isStale(false)
            .columns(
                List.of(
                    MatrixHeader.builder().id("s1").code("SEC-01").name("SEC-01").build(),
                    MatrixHeader.builder().id("s2").code("SEC-02").name("SEC-02").build()))
            .rows(
                List.of(
                    MatrixHeader.builder().id("c1").code("CLO1").name("=cmd|'/C calc'!A0").build()))
            .matrix(
                List.of(
                    List.of(
                        MatrixMetricCell.calculate(40, 36, 4, 0.85, 0.90, true, false),
                        MatrixMetricCell.empty(true, false))))
            .build();

    byte[] bytes = matrixExcelExporter.exportToXlsx(response);
    try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
      Sheet matrixSheet = wb.getSheet("MaTranPhanTich");
      Sheet detailSheet = wb.getSheet("DuLieuChiTiet");
      assertThat(matrixSheet).isNotNull();
      assertThat(detailSheet).isNotNull();

      Row passRateRow = findRowByCellValues(matrixSheet, 0, "CLO1", 2, "Tỷ lệ đạt (Evaluated)");
      assertThat(passRateRow).isNotNull();
      assertThat(passRateRow.getCell(1).getCellType()).isEqualTo(CellType.STRING);
      assertThat(passRateRow.getCell(1).getStringCellValue()).isEqualTo("'=cmd|'/C calc'!A0");
      assertThat(passRateRow.getCell(3).getNumericCellValue()).isEqualTo(0.90);
      assertThat(passRateRow.getCell(4)).isNull();

      Row detailSec1 = findRowByFirstCells(detailSheet, "CLO1", "SEC-01");
      assertThat(detailSec1).isNotNull();
      assertThat(detailSec1.getCell(2).getNumericCellValue()).isEqualTo(40.0);
      assertThat(detailSec1.getCell(7).getNumericCellValue()).isEqualTo(0.85);
      assertThat(detailSec1.getCell(8).getNumericCellValue()).isEqualTo(0.90);

      Row detailSec2 = findRowByFirstCells(detailSheet, "CLO1", "SEC-02");
      assertThat(detailSec2).isNotNull();
      assertThat(detailSec2.getCell(3).getNumericCellValue()).isEqualTo(0.0);
      assertThat(detailSec2.getCell(7)).isNull();
      assertThat(detailSec2.getCell(8)).isNull();
      assertThat(detailSec2.getCell(9)).isNull();
    }

    byte[] nullBytes = matrixExcelExporter.exportToXlsx(null);
    try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(nullBytes))) {
      Sheet matrixSheet = wb.getSheet("MaTranPhanTich");
      assertThat(
              findRowByFirstCell(
                  matrixSheet, "Không có dữ liệu phân tích phù hợp với điều kiện lọc."))
          .isNotNull();
    }
  }

  private static Row findRowByFirstCell(Sheet sheet, String firstVal) {
    return java.util.stream.StreamSupport.stream(sheet.spliterator(), false)
        .filter(r -> firstVal.equals(readString(r.getCell(0))))
        .findFirst()
        .orElse(null);
  }

  private static Row findRowByFirstCells(Sheet sheet, String firstVal, String secondVal) {
    return findRowByCellValues(sheet, 0, firstVal, 1, secondVal);
  }

  private static Row findRowByCellValues(
      Sheet sheet, int colA, String valA, int colB, String valB) {
    return java.util.stream.StreamSupport.stream(sheet.spliterator(), false)
        .filter(
            r ->
                valA.equals(readString(r.getCell(colA)))
                    && valB.equals(readString(r.getCell(colB))))
        .findFirst()
        .orElse(null);
  }

  private static String readString(Cell cell) {
    return cell != null && cell.getCellType() == CellType.STRING ? cell.getStringCellValue() : null;
  }
}
