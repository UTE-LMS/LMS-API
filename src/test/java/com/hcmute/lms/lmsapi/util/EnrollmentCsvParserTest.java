package com.hcmute.lms.lmsapi.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hcmute.lms.lmsapi.exception.AppException;
import com.hcmute.lms.lmsapi.exception.ErrorCode;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("EnrollmentCsvParser (US-20): đọc tệp CSV danh sách lớp")
class EnrollmentCsvParserTest {

  private static EnrollmentCsvParser.Result parse(String content) {
    return EnrollmentCsvParser.parse(
        new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
  }

  private static void assertRejected(String content, String messagePart) {
    assertThatThrownBy(() -> parse(content))
        .isInstanceOfSatisfying(
            AppException.class,
            ex -> {
              assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
              assertThat(ex.getMessage()).contains(messagePart);
            });
  }

  @Test
  @DisplayName("US-20/AC-1: đọc đủ ba cột; số dòng tính từ dòng tiêu đề là dòng 1")
  void parsesAllColumnsWithLineNumbers() {
    EnrollmentCsvParser.Result result =
        parse("studentRef,cohort,majorCode\nSV-1,K22,FINTECH\nSV-2,K23,IT\n");

    assertThat(result.rows()).hasSize(2);
    assertThat(result.rows().get(0))
        .isEqualTo(new EnrollmentCsvParser.Row(2, "SV-1", "K22", "FINTECH"));
    assertThat(result.rows().get(1)).isEqualTo(new EnrollmentCsvParser.Row(3, "SV-2", "K23", "IT"));
    assertThat(result.ignoredColumns()).isEmpty();
  }

  @Test
  @DisplayName("Chỉ cần cột studentRef; cột tiêu đề không phân biệt hoa thường và khoảng trắng")
  void onlyStudentRefIsRequired() {
    EnrollmentCsvParser.Result result = parse(" STUDENTREF \nSV-1\nSV-2\n");

    assertThat(result.rows())
        .extracting(EnrollmentCsvParser.Row::studentRef)
        .containsExactly("SV-1", "SV-2");
    assertThat(result.rows().get(0).cohort()).isEmpty();
    assertThat(result.rows().get(0).majorCode()).isEmpty();
  }

  @Test
  @DisplayName("Cột họ tên/email bị bỏ qua và được báo lại, không được đọc (Q09)")
  void personalDataColumnsAreIgnoredAndReported() {
    EnrollmentCsvParser.Result result =
        parse("studentRef,fullName,email,cohort\nSV-1,Nguyen Van A,a@example.invalid,K22\n");

    assertThat(result.ignoredColumns()).containsExactly("fullName", "email");
    assertThat(result.rows().get(0)).isEqualTo(new EnrollmentCsvParser.Row(2, "SV-1", "K22", ""));
  }

  @Test
  @DisplayName("Chấp nhận BOM UTF-8, phân cách chấm phẩy, dòng trống và CRLF")
  void acceptsBomSemicolonBlankLinesAndCrlf() {
    EnrollmentCsvParser.Result result =
        parse("﻿studentRef;cohort\r\nSV-1;K22\r\n\r\n   \r\nSV-2;K23\r\n");

    assertThat(result.rows()).hasSize(2);
    assertThat(result.rows().get(0).cohort()).isEqualTo("K22");
    // Dòng trống không làm lệch số dòng: SV-2 nằm ở dòng 5 của tệp
    assertThat(result.rows().get(1).lineNumber()).isEqualTo(5);
  }

  @Test
  @DisplayName("Hỗ trợ giá trị trong nháy kép, kể cả dấu phân cách và nháy kép đôi bên trong")
  void supportsQuotedValues() {
    EnrollmentCsvParser.Result result =
        parse("studentRef,cohort\n\"SV-1\",\"K22, chất lượng cao\"\nSV-2,\"a\"\"b\"\n");

    assertThat(result.rows().get(0).cohort()).isEqualTo("K22, chất lượng cao");
    assertThat(result.rows().get(1).cohort()).isEqualTo("a\"b");
  }

  @Test
  @DisplayName("Dòng thiếu ô: ô thiếu là chuỗi rỗng để service báo lỗi theo dòng")
  void shortRowsYieldEmptyCells() {
    EnrollmentCsvParser.Result result = parse("cohort,studentRef\nK22\nK23,SV-2\n");

    assertThat(result.rows().get(0).studentRef()).isEmpty();
    assertThat(result.rows().get(1).studentRef()).isEqualTo("SV-2");
  }

  @Test
  @DisplayName("US-20/AC-3: tệp rỗng, chỉ có khoảng trắng hoặc dòng đầu trống -> 400")
  void emptyFileIsRejected() {
    assertRejected("", "rỗng");
    assertRejected("   \n", "rỗng");
    assertRejected("\nstudentRef\nSV-1\n", "rỗng");
  }

  @Test
  @DisplayName("US-20/AC-3: thiếu cột studentRef hoặc lặp cột -> 400")
  void headerProblemsAreRejected() {
    assertRejected("cohort,majorCode\nK22,IT\n", "studentRef");
    assertRejected("studentRef,studentRef\nSV-1,SV-2\n", "nhiều lần");
  }

  @Test
  @DisplayName("US-20/AC-3: chỉ có dòng tiêu đề, không có dữ liệu -> 400")
  void headerOnlyIsRejected() {
    assertRejected("studentRef,cohort\n", "không có dòng dữ liệu");
    assertRejected("studentRef,cohort\n\n  \n", "không có dòng dữ liệu");
  }

  @Test
  @DisplayName("Giới hạn số dòng: đúng 5000 dòng được nhận, 5001 dòng bị từ chối")
  void rowLimitBoundary() {
    StringBuilder atLimit = new StringBuilder("studentRef\n");
    for (int i = 0; i < EnrollmentCsvParser.MAX_DATA_ROWS; i++) {
      atLimit.append("SV-").append(i).append('\n');
    }
    assertThat(parse(atLimit.toString()).rows()).hasSize(EnrollmentCsvParser.MAX_DATA_ROWS);

    assertRejected(atLimit.append("SV-extra\n").toString(), "vượt quá");
  }

  @Test
  @DisplayName("splitLine: nháy kép không đóng được coi là giá trị đến hết dòng (không ném lỗi)")
  void unterminatedQuoteIsLenient() {
    assertThat(EnrollmentCsvParser.splitLine("a,\"b,c", ',')).containsExactly("a", "b,c");
  }
}
