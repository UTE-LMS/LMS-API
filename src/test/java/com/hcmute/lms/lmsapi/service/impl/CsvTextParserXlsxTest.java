package com.hcmute.lms.lmsapi.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hcmute.lms.lmsapi.exception.BusinessRuleViolationException;
import com.hcmute.lms.lmsapi.util.XlsxCsvConverter;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

/**
 * CsvTextParser dùng chung cho nhiều chức năng nhập danh mục (người dùng, khoa, CTĐT, lớp...). Test
 * kiểm tra tệp .xlsx và .csv cùng nội dung cho cùng kết quả (ADR 0015). Dữ liệu giả.
 */
@DisplayName("ADR 0015: CsvTextParser đọc cả .xlsx")
class CsvTextParserXlsxTest {

  private static final String CSV =
      "code,name\nFIT,Công nghệ thông tin\nFBA,\"Kinh tế, quản trị\"\n";

  private static MockMultipartFile csvFile(String name) {
    return new MockMultipartFile("file", name, "text/csv", CSV.getBytes(StandardCharsets.UTF_8));
  }

  private static MockMultipartFile xlsxFile(String name) {
    byte[] xlsx = XlsxCsvConverter.fromCsv(CSV.getBytes(StandardCharsets.UTF_8));
    return new MockMultipartFile("file", name, "application/octet-stream", xlsx);
  }

  @Test
  @DisplayName(".xlsx và .csv cùng nội dung -> cùng cột và cùng dòng dữ liệu")
  void xlsxMatchesCsv() {
    CsvTextParser.ParsedCsv fromCsv = CsvTextParser.read(csvFile("khoa.csv"));
    CsvTextParser.ParsedCsv fromXlsx = CsvTextParser.read(xlsxFile("khoa.xlsx"));

    assertThat(fromXlsx.headerIndex()).isEqualTo(fromCsv.headerIndex());
    assertThat(fromXlsx.rows()).isEqualTo(fromCsv.rows());
    assertThat(CsvTextParser.cell(fromXlsx.headerIndex(), fromXlsx.rows().get(1), "name"))
        .isEqualTo("Kinh tế, quản trị");
  }

  @Test
  @DisplayName("Đuôi .XLSX viết hoa vẫn được nhận")
  void uppercaseExtension() {
    assertThat(CsvTextParser.read(xlsxFile("KHOA.XLSX")).rows()).hasSize(2);
  }

  @Test
  @DisplayName(".xls (Excel cũ) bị từ chối với hướng dẫn, không đọc nhầm như CSV")
  void legacyXlsRejected() {
    assertThatThrownBy(() -> CsvTextParser.read(csvFile("khoa.xls"))).hasMessageContaining(".xls");
  }

  @Test
  @DisplayName("Tệp .xlsx chỉ có dòng tiêu đề: vẫn báo thiếu dòng dữ liệu như CSV")
  void headerOnlyXlsx() {
    byte[] xlsx = XlsxCsvConverter.fromCsv("code,name\n".getBytes(StandardCharsets.UTF_8));
    MockMultipartFile file =
        new MockMultipartFile("file", "khoa.xlsx", "application/octet-stream", xlsx);

    assertThatThrownBy(() -> CsvTextParser.read(file))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessageContaining("ít nhất 1 dòng dữ liệu");
  }
}
