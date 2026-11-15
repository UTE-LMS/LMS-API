package com.hcmute.lms.lmsapi.exporter;

import com.hcmute.lms.lmsapi.dto.analytics.AnalyticsMatrixResponse;
import com.hcmute.lms.lmsapi.dto.analytics.MatrixHeader;
import com.hcmute.lms.lmsapi.dto.analytics.MatrixMetricCell;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Trình xuất dữ liệu ma trận phân tích đa chiều sang định dạng CSV (Milestone 1). Tuân thủ tiêu
 * chuẩn RFC 4180, gắn UTF-8 BOM (\uFEFF) để hiển thị tiếng Việt chuẩn trong Excel, và bảo toàn
 * tuyệt đối quy tắc mẫu số bằng 0 (xuất ra ô rỗng hoặc N/A, không bao giờ xuất 0.0 hay 0%).
 */
@Component
public class MatrixCsvExporter {

  private static final String UTF8_BOM = "\uFEFF";

  /**
   * Chuyển đổi dữ liệu ma trận sang mảng byte CSV chuẩn UTF-8 kèm BOM.
   *
   * @param response dữ liệu ma trận phân tích
   * @return mảng byte CSV
   */
  public byte[] exportToCsv(AnalyticsMatrixResponse response) {
    if (response == null) {
      return UTF8_BOM.getBytes(StandardCharsets.UTF_8);
    }

    StringBuilder sb = new StringBuilder(UTF8_BOM);

    // 1. Khối Metadata tiêu đề báo cáo
    sb.append(escape("BÁO CÁO PHÂN TÍCH ĐA CHIỀU (CROSS-TAB ANALYTICS)")).append("\n");
    sb.append(
            escape(
                "Chiều phân tích: "
                    + (response.getDimension() != null ? response.getDimension().name() : "")
                    + " | Cấp độ: "
                    + (response.getLevel() != null ? response.getLevel().name() : "")))
        .append("\n");
    sb.append(
            escape(
                "Lượt tính toán (RunId): "
                    + (response.getRunId() != null ? response.getRunId() : "N/A")
                    + " | Chính thức: "
                    + (Boolean.TRUE.equals(response.getIsOfficial()) ? "CÓ" : "KHÔNG")
                    + " | Bị cũ: "
                    + (Boolean.TRUE.equals(response.getIsStale()) ? "CÓ" : "KHÔNG")))
        .append("\n");
    sb.append(escape("Thời điểm xuất: " + Instant.now().toString())).append("\n\n");

    List<MatrixHeader> columns = response.getColumns();
    List<MatrixHeader> rows = response.getRows();
    List<List<MatrixMetricCell>> matrix = response.getMatrix();

    if (columns == null || columns.isEmpty() || rows == null || rows.isEmpty() || matrix == null) {
      sb.append(escape("Không có dữ liệu phân tích phù hợp với điều kiện lọc.")).append("\n");
      return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    // 2. Dòng tiêu đề cột chính
    sb.append(escape("Mã")).append(",").append(escape("Tên")).append(",").append(escape("Chỉ số"));
    for (MatrixHeader col : columns) {
      sb.append(",")
          .append(
              escape(
                  col.getCode() != null
                      ? col.getCode()
                      : (col.getName() != null ? col.getName() : "")));
    }
    sb.append("\n");

    // 3. Các dòng dữ liệu trong bảng ma trận
    for (int r = 0; r < rows.size(); r++) {
      MatrixHeader rowHeader = rows.get(r);
      String rowCode = rowHeader.getCode() != null ? rowHeader.getCode() : "";
      String rowName = rowHeader.getName() != null ? rowHeader.getName() : "";
      List<MatrixMetricCell> rowCells = r < matrix.size() ? matrix.get(r) : List.of();

      // Dòng chỉ số 1: Tỷ lệ đạt (trên SV được đánh giá)
      sb.append(escape(rowCode))
          .append(",")
          .append(escape(rowName))
          .append(",")
          .append(escape("Tỷ lệ đạt (Evaluated)"));
      for (int c = 0; c < columns.size(); c++) {
        MatrixMetricCell cell = (rowCells != null && c < rowCells.size()) ? rowCells.get(c) : null;
        sb.append(",").append(escape(formatPercent(cell != null ? cell.getPassRate() : null)));
      }
      sb.append("\n");

      // Dòng chỉ số 2: Tỷ lệ đạt (trên tổng SV ghi danh)
      sb.append(escape(rowCode))
          .append(",")
          .append(escape(rowName))
          .append(",")
          .append(escape("Tỷ lệ đạt (Enrolled)"));
      for (int c = 0; c < columns.size(); c++) {
        MatrixMetricCell cell = (rowCells != null && c < rowCells.size()) ? rowCells.get(c) : null;
        sb.append(",")
            .append(escape(formatPercent(cell != null ? cell.getPassRateOfEnrolled() : null)));
      }
      sb.append("\n");

      // Dòng chỉ số 3: Điểm trung bình (Mean)
      sb.append(escape(rowCode))
          .append(",")
          .append(escape(rowName))
          .append(",")
          .append(escape("Điểm trung bình"));
      for (int c = 0; c < columns.size(); c++) {
        MatrixMetricCell cell = (rowCells != null && c < rowCells.size()) ? rowCells.get(c) : null;
        sb.append(",").append(escape(formatNumber(cell != null ? cell.getMean() : null)));
      }
      sb.append("\n");

      // Dòng chỉ số 4: Số lượng Đánh giá / Ghi danh
      sb.append(escape(rowCode))
          .append(",")
          .append(escape(rowName))
          .append(",")
          .append(escape("Đánh giá / Ghi danh"));
      for (int c = 0; c < columns.size(); c++) {
        MatrixMetricCell cell = (rowCells != null && c < rowCells.size()) ? rowCells.get(c) : null;
        String counts =
            cell != null
                ? (cell.getEvaluated() != null ? cell.getEvaluated() : 0)
                    + " / "
                    + (cell.getEnrolled() != null ? cell.getEnrolled() : 0)
                : "0 / 0";
        sb.append(",").append(escape(counts));
      }
      sb.append("\n");
    }

    // 4. Bảng chi tiết chuẩn hóa (Normalized Detailed Table)
    sb.append("\n").append(escape("DỮ LIỆU CHI TIẾT (RAW METRIC RECORDS)")).append("\n");
    sb.append(
        "RowCode,ColCode,Enrolled,Evaluated,Passed,Failed,InsufficientData,Mean,PassRate,PassRateOfEnrolled,IsOfficial,IsStale\n");
    for (int r = 0; r < rows.size(); r++) {
      MatrixHeader rowHeader = rows.get(r);
      String rowCode = rowHeader.getCode() != null ? rowHeader.getCode() : "";
      List<MatrixMetricCell> rowCells = r < matrix.size() ? matrix.get(r) : List.of();

      for (int c = 0; c < columns.size(); c++) {
        MatrixHeader colHeader = columns.get(c);
        String colCode = colHeader.getCode() != null ? colHeader.getCode() : "";
        MatrixMetricCell cell = (rowCells != null && c < rowCells.size()) ? rowCells.get(c) : null;

        sb.append(escape(rowCode)).append(",");
        sb.append(escape(colCode)).append(",");
        sb.append(cell != null && cell.getEnrolled() != null ? cell.getEnrolled() : 0).append(",");
        sb.append(cell != null && cell.getEvaluated() != null ? cell.getEvaluated() : 0)
            .append(",");
        sb.append(cell != null && cell.getPassed() != null ? cell.getPassed() : 0).append(",");
        sb.append(cell != null && cell.getFailed() != null ? cell.getFailed() : 0).append(",");
        sb.append(
                cell != null && cell.getInsufficientData() != null ? cell.getInsufficientData() : 0)
            .append(",");
        sb.append(formatNumber(cell != null ? cell.getMean() : null)).append(",");
        sb.append(formatDecimal(cell != null ? cell.getPassRate() : null)).append(",");
        sb.append(formatDecimal(cell != null ? cell.getPassRateOfEnrolled() : null)).append(",");
        sb.append(cell != null && Boolean.TRUE.equals(cell.getIsOfficial()) ? "true" : "false")
            .append(",");
        sb.append(cell != null && Boolean.TRUE.equals(cell.getIsStale()) ? "true" : "false")
            .append("\n");
      }
    }

    return sb.toString().getBytes(StandardCharsets.UTF_8);
  }

  private String formatPercent(Double value) {
    if (value == null) {
      return "";
    }
    return String.format(Locale.US, "%.2f%%", value * 100.0);
  }

  private String formatNumber(Double value) {
    if (value == null) {
      return "";
    }
    return String.format(Locale.US, "%.3f", value);
  }

  private String formatDecimal(Double value) {
    if (value == null) {
      return "";
    }
    return String.format(Locale.US, "%.4f", value);
  }

  /**
   * Chuẩn hóa và thoát chuỗi ký tự theo tiêu chuẩn RFC 4180 và phòng chống tấn công chèn công thức
   * CSV (CWE-1236 / CSV Formula Injection). Nếu chuỗi bắt đầu bằng một trong các ký tự kích hoạt
   * công thức ('=', '+', '-', '@', '\t', '\r'), tự động thêm tiền tố nháy đơn (') để đảm bảo các
   * phần mềm bảng tính (Excel, Calc) hiển thị dưới dạng chuỗi thuần túy và không thực thi mã độc.
   *
   * @param raw chuỗi thô cần thoát
   * @return chuỗi đã được làm sạch và thoát an toàn
   */
  public String escape(String raw) {
    if (raw == null || raw.isEmpty()) {
      return "";
    }

    // Phòng chống tấn công chèn công thức CSV (CWE-1236 / CSV Formula Injection)
    char firstChar = raw.charAt(0);
    boolean startsWithFormulaChar = isFormulaTrigger(firstChar);
    if (!startsWithFormulaChar) {
      String trimmed = raw.trim();
      if (!trimmed.isEmpty() && isFormulaTrigger(trimmed.charAt(0))) {
        startsWithFormulaChar = true;
      }
    }

    if (startsWithFormulaChar) {
      raw = "'" + raw;
    }

    if (raw.contains(",") || raw.contains("\"") || raw.contains("\n") || raw.contains("\r")) {
      return "\"" + raw.replace("\"", "\"\"") + "\"";
    }
    return raw;
  }

  private boolean isFormulaTrigger(char c) {
    return c == '=' || c == '+' || c == '-' || c == '@' || c == '\t' || c == '\r';
  }
}
