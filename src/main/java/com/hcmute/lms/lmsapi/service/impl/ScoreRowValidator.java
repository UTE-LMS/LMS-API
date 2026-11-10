package com.hcmute.lms.lmsapi.service.impl;

import com.hcmute.lms.lmsapi.document.ImportColumnMapping;
import com.hcmute.lms.lmsapi.enums.EmptyCellAs;
import com.hcmute.lms.lmsapi.enums.ImportErrorType;
import com.hcmute.lms.lmsapi.enums.ScoreStatus;
import com.hcmute.lms.lmsapi.util.ScoreCsvParser;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Kiểm tra từng dòng của tệp điểm (US-22) và tạo ra các bản ghi điểm sẽ ghi (US-23). Lớp thuần:
 * nhận dữ liệu đã nạp sẵn, không truy cập CSDL, nên test được bằng ví dụ tính tay.
 *
 * <p>Quy tắc: ô điểm có chữ số thì là điểm (SCORED, 0 ≤ điểm ≤ maxScore); ô tồn tại nhưng để trống
 * thì theo {@code emptyCellAs} do người nhập chọn (BLANK = 0, MISSING = null); ô không tồn tại
 * (dòng ngắn hơn tiêu đề) là lỗi MISSING_FIELD chứ không tự hiểu là trống. Một dòng có bất kỳ lỗi
 * nào thì không sinh điểm nào, nhưng mọi lỗi của dòng đều được báo.
 */
@Component
public class ScoreRowValidator {

  /** Số thập phân đơn giản; dấu phẩy được hiểu là dấu thập phân (Excel tiếng Việt). */
  private static final Pattern NUMBER = Pattern.compile("^[+-]?\\d{1,9}([.,]\\d{1,6})?$");

  /** Cột điểm đã xác định vị trí trong tiêu đề và điểm tối đa của câu (snapshot). */
  public record Column(int index, String name, String itemId, double maxScore) {}

  /** Điểm sẽ ghi cho một (sinh viên, câu). {@code rawScore} null khi MISSING. */
  public record Draft(
      int sourceRow,
      String studentId,
      String itemId,
      ScoreStatus status,
      Double rawScore,
      double maxScore) {}

  /** Một lỗi theo dòng/cột. */
  public record RowError(
      int rowNumber, String column, ImportErrorType errorType, String rawValue, String message) {}

  /** Kết quả kiểm tra cả tệp. */
  public record Result(int totalRows, int errorRows, List<RowError> errors, List<Draft> drafts) {

    public Result {
      errors = List.copyOf(errors);
      drafts = List.copyOf(drafts);
    }

    @Override
    public List<RowError> errors() {
      return List.copyOf(errors);
    }

    @Override
    public List<Draft> drafts() {
      return List.copyOf(drafts);
    }
  }

  /** Dữ liệu dùng chung khi kiểm tra các dòng của một tệp. */
  private record Context(
      int headerCount,
      int studentIndex,
      String studentColumn,
      List<Column> columns,
      Map<String, String> enrolledStudentIdByRef,
      Set<String> existingKeys,
      EmptyCellAs emptyCellAs,
      Map<String, Integer> firstLineOfRef) {}

  /** Khóa nhận diện một (sinh viên, câu) để so với điểm đã có. */
  public static String key(String studentId, String itemId) {
    return studentId + ":" + itemId;
  }

  /**
   * @param studentIndex vị trí cột mã sinh viên trong tiêu đề
   * @param enrolledStudentIdByRef mã sinh viên (studentRef) -> studentId, chỉ sinh viên ENROLLED
   * @param existingKeys các {@link #key} đã có điểm trong (lớp, bài)
   */
  public Result validate(
      ScoreCsvParser.Table table,
      int studentIndex,
      ImportColumnMapping mapping,
      List<Column> columns,
      Map<String, String> enrolledStudentIdByRef,
      Set<String> existingKeys,
      EmptyCellAs emptyCellAs) {
    Context ctx =
        new Context(
            table.headers().size(),
            studentIndex,
            mapping.getStudentColumn(),
            List.copyOf(columns),
            enrolledStudentIdByRef,
            existingKeys,
            emptyCellAs,
            new HashMap<>());
    List<RowError> errors = new ArrayList<>();
    List<Draft> drafts = new ArrayList<>();
    Set<Integer> errorRows = new HashSet<>();
    for (ScoreCsvParser.Row row : table.rows()) {
      List<RowError> rowErrors = new ArrayList<>();
      List<Draft> rowDrafts = new ArrayList<>();
      validateRow(row, ctx, rowErrors, rowDrafts);
      if (rowErrors.isEmpty()) {
        drafts.addAll(rowDrafts);
      } else {
        errors.addAll(rowErrors);
        errorRows.add(row.lineNumber());
      }
    }
    return new Result(table.rows().size(), errorRows.size(), errors, drafts);
  }

  private void validateRow(
      ScoreCsvParser.Row row, Context ctx, List<RowError> errors, List<Draft> drafts) {
    int cellCount = row.cells().size();
    if (cellCount > ctx.headerCount()) {
      errors.add(
          new RowError(
              row.lineNumber(),
              null,
              ImportErrorType.MISSING_FIELD,
              null,
              "Dòng có " + cellCount + " ô, nhiều hơn " + ctx.headerCount() + " cột tiêu đề"));
      return;
    }
    String studentId = checkStudent(row, ctx, errors);
    for (Column column : ctx.columns()) {
      checkScore(row, column, studentId, ctx, errors, drafts);
    }
  }

  /** Trả về studentId nếu mã sinh viên hợp lệ, ngược lại ghi lỗi và trả về null. */
  private String checkStudent(ScoreCsvParser.Row row, Context ctx, List<RowError> errors) {
    String ref = cellAt(row, ctx.studentIndex());
    String column = ctx.studentColumn();
    if (ref == null || ref.isEmpty()) {
      errors.add(
          new RowError(
              row.lineNumber(), column, ImportErrorType.MISSING_FIELD, ref, "Thiếu mã sinh viên"));
      return null;
    }
    Integer firstLine = ctx.firstLineOfRef().putIfAbsent(ref, row.lineNumber());
    if (firstLine != null) {
      errors.add(
          new RowError(
              row.lineNumber(),
              column,
              ImportErrorType.DUPLICATE_RECORD,
              ref,
              "Mã sinh viên trùng với dòng " + firstLine + " trong tệp"));
      return null;
    }
    String studentId = ctx.enrolledStudentIdByRef().get(ref);
    if (studentId == null) {
      errors.add(
          new RowError(
              row.lineNumber(),
              column,
              ImportErrorType.INVALID_STUDENT_CODE,
              ref,
              "Mã sinh viên không có trong danh sách đang ghi danh (ENROLLED) của lớp"));
    }
    return studentId;
  }

  private void checkScore(
      ScoreCsvParser.Row row,
      Column column,
      String studentId,
      Context ctx,
      List<RowError> errors,
      List<Draft> drafts) {
    String cell = cellAt(row, column.index());
    if (cell == null) {
      errors.add(
          new RowError(
              row.lineNumber(),
              column.name(),
              ImportErrorType.MISSING_FIELD,
              null,
              "Dòng ngắn hơn dòng tiêu đề, thiếu ô điểm của cột này"));
      return;
    }
    ScoreStatus status;
    Double rawScore;
    if (cell.isEmpty()) {
      boolean blank = ctx.emptyCellAs() == EmptyCellAs.BLANK;
      status = blank ? ScoreStatus.BLANK : ScoreStatus.MISSING;
      rawScore = blank ? 0.0 : null;
    } else {
      String problem = scoreProblem(cell, column.maxScore());
      if (problem != null) {
        errors.add(
            new RowError(
                row.lineNumber(),
                column.name(),
                ImportErrorType.SCORE_OUT_OF_RANGE,
                cell,
                problem));
        return;
      }
      status = ScoreStatus.SCORED;
      rawScore = toNumber(cell).doubleValue();
    }
    if (studentId == null) {
      return;
    }
    if (ctx.existingKeys().contains(key(studentId, column.itemId()))) {
      errors.add(
          new RowError(
              row.lineNumber(),
              column.name(),
              ImportErrorType.DUPLICATE_RECORD,
              cell,
              "Sinh viên đã có điểm ở câu này; chính sách REJECT_ON_CONFLICT không cho nhập lại"));
      return;
    }
    drafts.add(
        new Draft(
            row.lineNumber(), studentId, column.itemId(), status, rawScore, column.maxScore()));
  }

  /** Mô tả lỗi của ô điểm có chữ, hoặc null nếu điểm hợp lệ (0 ≤ điểm ≤ maxScore). */
  private String scoreProblem(String cell, double maxScore) {
    if (!NUMBER.matcher(cell).matches()) {
      return "Không phải số hợp lệ";
    }
    BigDecimal value = toNumber(cell);
    if (value.signum() < 0) {
      return "Điểm âm";
    }
    if (value.compareTo(BigDecimal.valueOf(maxScore)) > 0) {
      return "Điểm vượt điểm tối đa "
          + BigDecimal.valueOf(maxScore).stripTrailingZeros().toPlainString();
    }
    return null;
  }

  private BigDecimal toNumber(String cell) {
    return new BigDecimal(cell.replace(',', '.'));
  }

  /** Giá trị ô, hoặc null nếu dòng ngắn hơn vị trí cần đọc. */
  private String cellAt(ScoreCsvParser.Row row, int index) {
    List<String> cells = row.cells();
    return index < cells.size() ? cells.get(index) : null;
  }
}
