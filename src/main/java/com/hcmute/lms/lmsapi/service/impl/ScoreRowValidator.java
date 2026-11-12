package com.hcmute.lms.lmsapi.service.impl;

import com.hcmute.lms.lmsapi.document.ImportColumnMapping;
import com.hcmute.lms.lmsapi.enums.ImportErrorType;
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
 * Kiểm tra từng dòng của tệp điểm (ADR 0014, v0.7).
 * Ô trống hoặc vắng thi mặc định rawScore = 0.0.
 */
@Component
public class ScoreRowValidator {

  private static final Pattern NUMBER = Pattern.compile("^[+-]?\\d{1,9}([.,]\\d{1,6})?$");

  public record Column(int index, String name, String itemId, String cloId, double maxScore) {}

  public record Draft(
      int sourceRow,
      String studentId,
      String itemId,
      String cloId,
      double rawScore,
      double maxScore) {}

  public record RowError(
      int rowNumber, String column, ImportErrorType errorType, String rawValue, String message) {}

  public record Result(int totalRows, int errorRows, List<RowError> errors, List<Draft> drafts) {
    public Result {
      errors = List.copyOf(errors);
      drafts = List.copyOf(drafts);
    }
  }

  private record Context(
      int headerCount,
      int studentIndex,
      String studentColumn,
      List<Column> columns,
      Map<String, String> enrolledStudentIdByRef,
      Set<String> existingKeys,
      Map<String, Integer> firstLineOfRef) {}

  public static String key(String studentId, String itemId, String cloId) {
    return studentId + ":" + itemId + ":" + cloId;
  }

  public Result validate(
      ScoreCsvParser.Table table,
      int studentIndex,
      ImportColumnMapping mapping,
      List<Column> columns,
      Map<String, String> enrolledStudentIdByRef,
      Set<String> existingKeys) {
    Context ctx =
        new Context(
            table.headers().size(),
            studentIndex,
            mapping.getStudentColumn(),
            List.copyOf(columns),
            enrolledStudentIdByRef,
            existingKeys,
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
    double rawScore;
    if (cell.isEmpty()) {
      rawScore = 0.0;
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
      rawScore = toNumber(cell).doubleValue();
    }
    if (studentId == null) {
      return;
    }
    if (ctx.existingKeys().contains(key(studentId, column.itemId(), column.cloId()))) {
      errors.add(
          new RowError(
              row.lineNumber(),
              column.name(),
              ImportErrorType.DUPLICATE_RECORD,
              cell,
              "Sinh viên đã có điểm ở câu/CLO này"));
      return;
    }
    drafts.add(
        new Draft(
            row.lineNumber(),
            studentId,
            column.itemId(),
            column.cloId(),
            rawScore,
            column.maxScore()));
  }

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

  private String cellAt(ScoreCsvParser.Row row, int index) {
    List<String> cells = row.cells();
    return index < cells.size() ? cells.get(index) : null;
  }
}
