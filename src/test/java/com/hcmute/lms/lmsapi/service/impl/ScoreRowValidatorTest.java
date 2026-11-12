package com.hcmute.lms.lmsapi.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.hcmute.lms.lmsapi.document.ImportColumnMapping;
import com.hcmute.lms.lmsapi.enums.ImportErrorType;
import com.hcmute.lms.lmsapi.util.ScoreCsvParser;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Giá trị kỳ vọng được tính tay từ nội dung CSV nhỏ trong từng test (ADR 0014, v0.7).
 */
@DisplayName("F05b: ScoreRowValidator kiểm tra từng dòng điểm")
class ScoreRowValidatorTest {

  private static final String ITEM_Q1 = "aaaaaaaaaaaaaaaaaaaaaaa1";
  private static final String ITEM_Q2 = "aaaaaaaaaaaaaaaaaaaaaaa2";
  private static final String CLO_1 = "ccccccccccccccccccccccc1";
  private static final String CLO_2 = "ccccccccccccccccccccccc2";

  private final ScoreRowValidator validator = new ScoreRowValidator();
  private final Map<String, String> enrolled = new HashMap<>();
  private final Set<String> existing = new HashSet<>();

  private final ImportColumnMapping mapping =
      ImportColumnMapping.builder().studentColumn("studentRef").build();
  private final List<ScoreRowValidator.Column> columns =
      List.of(
          new ScoreRowValidator.Column(1, "Q1", ITEM_Q1, CLO_1, 4.0),
          new ScoreRowValidator.Column(2, "Q2", ITEM_Q2, CLO_2, 6.0));

  ScoreRowValidatorTest() {
    enrolled.put("S1", "id-s1");
    enrolled.put("S2", "id-s2");
    enrolled.put("S3", "id-s3");
  }

  private ScoreRowValidator.Result run(String csv) {
    return validator.validate(
        ScoreCsvParser.parse(csv.getBytes(StandardCharsets.UTF_8)),
        0,
        mapping,
        columns,
        enrolled,
        existing);
  }

  @Test
  @DisplayName("US-22: điểm đúng bằng maxScore là hợp lệ; 0 gõ rõ ràng là 0.0")
  void scoresAtBoundaryAreValid() {
    ScoreRowValidator.Result result = run("studentRef,Q1,Q2\nS1,4,6\nS2,0,5.5\n");

    assertThat(result.errors()).isEmpty();
    assertThat(result.totalRows()).isEqualTo(2);
    assertThat(result.errorRows()).isZero();
    assertThat(result.drafts())
        .containsExactly(
            new ScoreRowValidator.Draft(2, "id-s1", ITEM_Q1, CLO_1, 4.0, 4.0),
            new ScoreRowValidator.Draft(2, "id-s1", ITEM_Q2, CLO_2, 6.0, 6.0),
            new ScoreRowValidator.Draft(3, "id-s2", ITEM_Q1, CLO_1, 0.0, 4.0),
            new ScoreRowValidator.Draft(3, "id-s2", ITEM_Q2, CLO_2, 5.5, 6.0));
  }

  @Test
  @DisplayName("US-22/AC-1: maxScore + 0,01 là SCORE_OUT_OF_RANGE đúng dòng 2, cột Q1, kèm giá trị gốc")
  void scoreJustAboveMaxIsReported() {
    ScoreRowValidator.Result result = run("studentRef,Q1,Q2\nS1,4.01,6\nS2,4,6\n");

    assertThat(result.errors())
        .containsExactly(
            new ScoreRowValidator.RowError(
                2, "Q1", ImportErrorType.SCORE_OUT_OF_RANGE, "4.01", "Điểm vượt điểm tối đa 4"));
    assertThat(result.errorRows()).isEqualTo(1);
    assertThat(result.drafts()).extracting(ScoreRowValidator.Draft::sourceRow).containsOnly(3);
  }

  @Test
  @DisplayName("US-22/AC-1: điểm âm, chữ, NaN, ký hiệu khoa học, nhiều dấu thập phân đều lỗi; dấu phẩy thập phân hợp lệ")
  void invalidNumbersAreRejected() {
    for (String bad : new String[] {"-0.5", "abc", "NaN", "Infinity", "1e1", "1.2.3", "1 2"}) {
      ScoreRowValidator.Result result = run("studentRef,Q1,Q2\nS1," + bad + ",6\n");
      assertThat(result.errors())
          .as("giá trị %s", bad)
          .singleElement()
          .satisfies(
              error -> {
                assertThat(error.errorType()).isEqualTo(ImportErrorType.SCORE_OUT_OF_RANGE);
                assertThat(error.column()).isEqualTo("Q1");
                assertThat(error.rawValue()).isEqualTo(bad);
              });
    }
    ScoreRowValidator.Result comma = run("studentRef;Q1;Q2\nS1;3,5;6\n");
    assertThat(comma.errors()).isEmpty();
    assertThat(comma.drafts().get(0).rawScore()).isEqualTo(3.5);
    assertThat(run("studentRef;Q1;Q2\nS1;4,01;6\n").errors())
        .singleElement()
        .extracting("errorType")
        .isEqualTo(ImportErrorType.SCORE_OUT_OF_RANGE);
  }

  @Test
  @DisplayName("US-23/AC-1: ô trống tự động gán rawScore = 0.0 theo chuẩn v0.7")
  void emptyCellFollowsChosenPolicy() {
    String csv = "studentRef,Q1,Q2\nS1,,6\n";

    ScoreRowValidator.Draft asBlank = run(csv).drafts().get(0);
    assertThat(asBlank.rawScore()).isEqualTo(0.0);
    assertThat(asBlank.maxScore()).isEqualTo(4.0);
    assertThat(run(csv).drafts().get(1).rawScore()).isEqualTo(6.0);
  }

  @Test
  @DisplayName("US-22/AC-2: dòng ngắn hơn tiêu đề là MISSING_FIELD (khác ô trống); dòng thừa ô là lỗi cả dòng")
  void shortAndLongRowsAreMissingField() {
    ScoreRowValidator.Result shortRow = run("studentRef,Q1,Q2\nS1,4\n");
    assertThat(shortRow.errors())
        .containsExactly(
            new ScoreRowValidator.RowError(
                2,
                "Q2",
                ImportErrorType.MISSING_FIELD,
                null,
                "Dòng ngắn hơn dòng tiêu đề, thiếu ô điểm của cột này"));

    ScoreRowValidator.Result longRow = run("studentRef,Q1,Q2\nS1,4,6,7\n");
    assertThat(longRow.errors())
        .singleElement()
        .satisfies(
            error -> {
              assertThat(error.errorType()).isEqualTo(ImportErrorType.MISSING_FIELD);
              assertThat(error.column()).isNull();
              assertThat(error.message()).contains("4 ô").contains("3 cột");
            });
    assertThat(longRow.drafts()).isEmpty();
  }

  @Test
  @DisplayName("US-22/AC-2: mã sinh viên trống, lạ/không ghi danh, trùng trong tệp -> đúng loại lỗi")
  void studentCodeErrors() {
    ScoreRowValidator.Result result = run("studentRef,Q1,Q2\n,4,6\nS9,4,6\nS1,4,6\nS1,3,5\n");

    assertThat(result.errors())
        .extracting(
            ScoreRowValidator.RowError::rowNumber,
            ScoreRowValidator.RowError::column,
            ScoreRowValidator.RowError::errorType,
            ScoreRowValidator.RowError::rawValue)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(2, "studentRef", ImportErrorType.MISSING_FIELD, ""),
            org.assertj.core.groups.Tuple.tuple(
                3, "studentRef", ImportErrorType.INVALID_STUDENT_CODE, "S9"),
            org.assertj.core.groups.Tuple.tuple(
                5, "studentRef", ImportErrorType.DUPLICATE_RECORD, "S1"));
    assertThat(result.errors().get(2).message()).contains("dòng 4");
    assertThat(result.errorRows()).isEqualTo(3);
    assertThat(result.drafts()).extracting(ScoreRowValidator.Draft::sourceRow).containsOnly(4);
  }

  @Test
  @DisplayName("US-23/AC-2: (sinh viên, câu, CLO) đã có điểm -> DUPLICATE_RECORD")
  void existingScoreIsRejectedByPolicy() {
    existing.add(ScoreRowValidator.key("id-s1", ITEM_Q2, CLO_2));

    ScoreRowValidator.Result result = run("studentRef,Q1,Q2\nS1,4,6\nS2,1,2\n");

    assertThat(result.errors())
        .singleElement()
        .satisfies(
            error -> {
              assertThat(error.rowNumber()).isEqualTo(2);
              assertThat(error.column()).isEqualTo("Q2");
              assertThat(error.errorType()).isEqualTo(ImportErrorType.DUPLICATE_RECORD);
              assertThat(error.rawValue()).isEqualTo("6");
            });
    assertThat(result.drafts())
        .extracting(ScoreRowValidator.Draft::studentId)
        .containsOnly("id-s2");
  }

  @Test
  @DisplayName("US-22: một dòng nhiều lỗi thì báo đủ từng lỗi nhưng chỉ tính 1 dòng lỗi")
  void allErrorsOfARowAreReported() {
    ScoreRowValidator.Result result = run("studentRef,Q1,Q2\nS9,abc,-1\n");

    assertThat(result.errors())
        .extracting(ScoreRowValidator.RowError::errorType)
        .containsExactly(
            ImportErrorType.INVALID_STUDENT_CODE,
            ImportErrorType.SCORE_OUT_OF_RANGE,
            ImportErrorType.SCORE_OUT_OF_RANGE);
    assertThat(result.errorRows()).isEqualTo(1);
    assertThat(result.totalRows()).isEqualTo(1);
  }

  @Test
  @DisplayName("US-22/AC-3 (biên): 1 lỗi trong 1.000 dòng -> đúng 1 lỗi ở dòng 501, 999 dòng hợp lệ")
  void oneErrorInAThousandRows() {
    List<ScoreRowValidator.Column> single = List.of(columns.get(0));
    StringBuilder csv = new StringBuilder("studentRef,Q1\n");
    Map<String, String> many = new HashMap<>();
    for (int i = 1; i <= 1000; i++) {
      many.put("R" + i, "id-r" + i);
      csv.append("R").append(i).append(',').append(i == 500 ? "4.01" : "3").append('\n');
    }

    ScoreRowValidator.Result result =
        validator.validate(
            ScoreCsvParser.parse(csv.toString().getBytes(StandardCharsets.UTF_8)),
            0,
            mapping,
            single,
            many,
            existing);

    assertThat(result.totalRows()).isEqualTo(1000);
    assertThat(result.errorRows()).isEqualTo(1);
    assertThat(result.errors()).singleElement().extracting("rowNumber").isEqualTo(501);
    assertThat(result.drafts()).hasSize(999);
  }

  @Test
  @DisplayName("US-24: lớp 45 ghi danh, tệp chỉ có 40 dòng -> 40 điểm, 5 sinh viên không có điểm và không bị báo lỗi")
  void enrolledStudentsAbsentFromTheFileGetNoScores() {
    List<ScoreRowValidator.Column> single = List.of(columns.get(0));
    Map<String, String> classOf45 = new HashMap<>();
    StringBuilder csv = new StringBuilder("studentRef,Q1\n");
    for (int i = 1; i <= 45; i++) {
      classOf45.put("R" + i, "id-r" + i);
      if (i <= 40) {
        csv.append("R").append(i).append(",2\n");
      }
    }

    ScoreRowValidator.Result result =
        validator.validate(
            ScoreCsvParser.parse(csv.toString().getBytes(StandardCharsets.UTF_8)),
            0,
            mapping,
            single,
            classOf45,
            existing);

    assertThat(result.errors()).isEmpty();
    assertThat(result.drafts()).hasSize(40);
    assertThat(result.drafts())
        .extracting(ScoreRowValidator.Draft::studentId)
        .doesNotContain("id-r41", "id-r42", "id-r43", "id-r44", "id-r45");
  }

  @Test
  @DisplayName("Result trả danh sách bất biến")
  void resultListsAreImmutable() {
    ScoreRowValidator.Result result = run("studentRef,Q1,Q2\nS1,4,6\n");

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> result.drafts().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> result.errors().add(null))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
