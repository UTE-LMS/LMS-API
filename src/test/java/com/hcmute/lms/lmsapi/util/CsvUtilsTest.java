package com.hcmute.lms.lmsapi.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CsvUtils Unit Tests (RFC 4180 Pure Java Parser)")
class CsvUtilsTest {

  @Test
  @DisplayName("Parse CSV tiêu chuẩn có nhiều dòng, dấu phẩy phân cách")
  void parse_standardCsv_success() throws IOException {
    String csv = "code,name,credits\n" + "MATH101,Toán 1,3\n" + "PHYS101,Vật lý 1,4\n";

    List<List<String>> records =
        CsvUtils.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

    assertThat(records).hasSize(3);
    assertThat(records.get(0)).containsExactly("code", "name", "credits");
    assertThat(records.get(1)).containsExactly("MATH101", "Toán 1", "3");
    assertThat(records.get(2)).containsExactly("PHYS101", "Vật lý 1", "4");
  }

  @Test
  @DisplayName("Parse CSV có dấu ngoặc kép bọc chuỗi chứa dấu phẩy và escaped quotes")
  void parse_quotedFieldsWithCommasAndEscapedQuotes() throws IOException {
    String csv =
        "code,name,credits\n"
            + "INSE3302,\"Công nghệ phần mềm, hướng dịch vụ\",4\n"
            + "COMP101,\"Nhập môn \"\"Lập trình\"\" CNTT\",3\n";

    List<List<String>> records =
        CsvUtils.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

    assertThat(records).hasSize(3);
    assertThat(records.get(1))
        .containsExactly("INSE3302", "Công nghệ phần mềm, hướng dịch vụ", "4");
    assertThat(records.get(2)).containsExactly("COMP101", "Nhập môn \"Lập trình\" CNTT", "3");
  }

  @Test
  @DisplayName("Parse CSV có ký tự UTF-8 BOM ở đầu tệp (thường gặp khi xuất từ Excel/Windows)")
  void parse_utf8WithBom_success() throws IOException {
    byte[] bom = new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    byte[] content = "code,name,credits\nCS101,Lập trình C,3".getBytes(StandardCharsets.UTF_8);

    byte[] combined = new byte[bom.length + content.length];
    System.arraycopy(bom, 0, combined, 0, bom.length);
    System.arraycopy(content, 0, combined, bom.length, content.length);

    List<List<String>> records = CsvUtils.parse(new ByteArrayInputStream(combined));

    assertThat(records).hasSize(2);
    assertThat(records.get(0).get(0)).isEqualTo("code");
    assertThat(records.get(1)).containsExactly("CS101", "Lập trình C", "3");
  }

  @Test
  @DisplayName("Parse CSV với ký tự xuống dòng kiểu Windows (CRLF)")
  void parse_windowsCrlf_success() throws IOException {
    String csv = "code,name,credits\r\nMATH101,Toán 1,3\r\nPHYS101,Vật lý 1,4\r\n";

    List<List<String>> records =
        CsvUtils.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

    assertThat(records).hasSize(3);
    assertThat(records.get(1)).containsExactly("MATH101", "Toán 1", "3");
  }

  @Test
  @DisplayName("Bỏ qua các dòng trống ở cuối tệp hoặc giữa các dòng")
  void parse_skipsEmptyLines() throws IOException {
    String csv = "code,name,credits\n\n" + "MATH101,Toán 1,3\n\n\n";

    List<List<String>> records =
        CsvUtils.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

    assertThat(records).hasSize(2);
    assertThat(records.get(1)).containsExactly("MATH101", "Toán 1", "3");
  }

  @Test
  @DisplayName("Ném ngoại lệ khi dấu ngoặc kép không được đóng đúng quy cách (unclosed quotes)")
  void parse_unclosedQuotes_throwsException() {
    String csv = "code,name,credits\n" + "MATH101,\"Toán 1,3\n";

    assertThatThrownBy(
            () -> CsvUtils.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("dấu ngoặc kép không được đóng");
  }
}
