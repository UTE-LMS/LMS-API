package com.hcmute.lms.lmsapi.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hcmute.lms.lmsapi.dto.AssessmentScoreTemplate;
import com.hcmute.lms.lmsapi.exception.AppException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

@DisplayName("ADR 0015: ImportTemplateResponses (tải tệp mẫu .csv hoặc .xlsx)")
class ImportTemplateResponsesTest {

  private static final byte[] CSV =
      "﻿code,name\r\nOOPR230279,Lập trình\r\n".getBytes(StandardCharsets.UTF_8);

  private static AssessmentScoreTemplate template() {
    return new AssessmentScoreTemplate("courses-template.csv", CSV);
  }

  @Test
  @DisplayName("Mặc định (không chọn format) và format=csv giữ nguyên tệp CSV như trước")
  void csvIsUnchanged() {
    for (String format : new String[] {null, "csv", " CSV "}) {
      ResponseEntity<byte[]> response = ImportTemplateResponses.of(template(), format);

      assertThat(response.getBody()).isEqualTo(CSV);
      assertThat(response.getHeaders().getContentType().toString()).startsWith("text/csv");
      assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
          .isEqualTo("attachment; filename=\"courses-template.csv\"");
    }
  }

  @Test
  @DisplayName("format=xlsx: đổi đuôi tên tệp, đúng kiểu nội dung và đọc lại ra đúng dữ liệu mẫu")
  void xlsxIsConvertedFromCsv() {
    ResponseEntity<byte[]> response = ImportTemplateResponses.of(template(), "xlsx");

    assertThat(response.getHeaders().getContentType().toString())
        .isEqualTo(ImportTemplateResponses.XLSX_MEDIA_TYPE);
    assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
        .isEqualTo("attachment; filename=\"courses-template.xlsx\"");
    assertThat(new String(XlsxCsvConverter.toCsv(response.getBody()), StandardCharsets.UTF_8))
        .isEqualTo("code,name\nOOPR230279,Lập trình\n");
  }

  @Test
  @DisplayName("format lạ bị từ chối 400")
  void unknownFormatIsRejected() {
    assertThatThrownBy(() -> ImportTemplateResponses.of(template(), "pdf"))
        .isInstanceOf(AppException.class)
        .hasMessageContaining("csv hoặc xlsx");
  }
}
