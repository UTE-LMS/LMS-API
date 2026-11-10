package com.hcmute.lms.lmsapi.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hcmute.lms.lmsapi.exception.AppException;
import com.hcmute.lms.lmsapi.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("F05b: ScoreCsvParser đọc tệp CSV điểm (US-21, US-22/AC-4)")
class ScoreCsvParserTest {

  private static byte[] bytes(String text) {
    return text.getBytes(StandardCharsets.UTF_8);
  }

  private static void assertFileError(String csv, String messagePart) {
    assertThatThrownBy(() -> ScoreCsvParser.parse(bytes(csv)))
        .isInstanceOfSatisfying(
            AppException.class,
            ex -> {
              assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
              assertThat(ex.getHttpStatus().value()).isEqualTo(400);
              assertThat(ex.getMessage()).contains(messagePart);
            });
  }

  @Test
  @DisplayName("US-22/AC-4: tệp rỗng, chỉ có dòng trống hoặc chỉ có tiêu đề bị từ chối với 400")
  void emptyOrHeaderOnlyFilesAreRejected() {
    assertFileError("", "rỗng");
    assertFileError("\n\n", "rỗng");
    assertFileError("studentRef,Q1\n", "không có dòng dữ liệu");
    assertFileError("studentRef,Q1\n\n   \n", "không có dòng dữ liệu");
  }

  @Test
  @DisplayName("US-22/AC-4: tiêu đề trùng (không phân biệt hoa thường) bị từ chối")
  void duplicateHeadersAreRejected() {
    assertFileError("studentRef,Q1,q1\nA,1,2\n", "xuất hiện nhiều lần");
  }

  @Test
  @DisplayName("US-21: số dòng là dòng vật lý (tiêu đề = 1), dòng trống bị bỏ nhưng vẫn được đếm")
  void lineNumbersArePhysicalLines() {
    // dòng 1: tiêu đề; dòng 2: A; dòng 3: trống; dòng 4: B
    ScoreCsvParser.Table table = ScoreCsvParser.parse(bytes("studentRef,Q1\nA,1\n\nB,2\n"));

    assertThat(table.headers()).containsExactly("studentRef", "Q1");
    assertThat(table.rows()).hasSize(2);
    assertThat(table.rows().get(0).lineNumber()).isEqualTo(2);
    assertThat(table.rows().get(1).lineNumber()).isEqualTo(4);
    assertThat(table.rows().get(1).cells()).containsExactly("B", "2");
  }

  @Test
  @DisplayName("US-21: chấp nhận BOM, CRLF, chấm phẩy, nháy kép và bỏ khoảng trắng hai đầu ô")
  void acceptsBomSemicolonQuotesAndTrims() {
    ScoreCsvParser.Table table =
        ScoreCsvParser.parse(bytes("﻿studentRef;\"Câu 1.5\"; Q2 \r\n A1 ;\"8,5\";\r\n"));

    assertThat(table.headers()).containsExactly("studentRef", "Câu 1.5", "Q2");
    // Ô cuối để trống vẫn là một ô (rỗng), không bị coi là thiếu ô.
    assertThat(table.rows().get(0).cells()).containsExactly("A1", "8,5", "");
  }

  @Test
  @DisplayName("US-21: dòng thừa/thiếu ô vẫn đọc được nguyên số ô để service báo lỗi theo dòng")
  void rowsKeepTheirOwnCellCount() {
    ScoreCsvParser.Table table = ScoreCsvParser.parse(bytes("studentRef,Q1\nA\nB,1,2,3\n"));

    assertThat(table.rows().get(0).cells()).hasSize(1);
    assertThat(table.rows().get(1).cells()).hasSize(4);
  }

  @Test
  @DisplayName("US-21: indexOf tìm cột không phân biệt hoa thường, -1 khi không có hoặc null")
  void indexOfIsCaseInsensitive() {
    ScoreCsvParser.Table table = ScoreCsvParser.parse(bytes("StudentRef,Q1\nA,1\n"));

    assertThat(table.indexOf("studentref")).isZero();
    assertThat(table.indexOf(" Q1 ")).isEqualTo(1);
    assertThat(table.indexOf("Q9")).isEqualTo(-1);
    assertThat(table.indexOf(null)).isEqualTo(-1);
  }

  @Test
  @DisplayName("US-22/AC-4 (biên): đúng 5.000 dòng dữ liệu được nhận, 5.001 dòng bị từ chối")
  void rowLimitBoundary() {
    StringBuilder atLimit = new StringBuilder("studentRef,Q1\n");
    for (int i = 0; i < EnrollmentCsvParser.MAX_DATA_ROWS; i++) {
      atLimit.append("S").append(i).append(",1\n");
    }
    assertThat(ScoreCsvParser.parse(bytes(atLimit.toString())).rows())
        .hasSize(EnrollmentCsvParser.MAX_DATA_ROWS);

    assertFileError(atLimit + "SX,1\n", "vượt quá");
  }

  @Test
  @DisplayName("Table và Row trả danh sách bất biến: không sửa được dữ liệu bên trong")
  void resultsAreImmutable() {
    ScoreCsvParser.Table table = ScoreCsvParser.parse(bytes("studentRef,Q1\nA,1\n"));

    List<String> headers = table.headers();
    assertThatThrownBy(() -> headers.add("x")).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> table.rows().get(0).cells().add("x"))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
