package com.hcmute.lms.lmsapi.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hcmute.lms.lmsapi.exception.AppException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit test cầu nối Excel/CSV (ADR 0015). Tệp .xlsx được dựng ngay trong test bằng POI, dữ liệu
 * giả; giá trị kỳ vọng viết tay theo cách Excel hiển thị ô.
 */
@DisplayName("ADR 0015: XlsxCsvConverter và ImportFiles")
class XlsxCsvConverterTest {

  private interface SheetFiller {
    void fill(XSSFWorkbook workbook) throws IOException;
  }

  private static byte[] xlsx(SheetFiller filler) {
    try (XSSFWorkbook workbook = new XSSFWorkbook();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      filler.fill(workbook);
      workbook.write(out);
      return out.toByteArray();
    } catch (IOException ex) {
      throw new IllegalStateException(ex);
    }
  }

  private static void text(Sheet sheet, int rowIndex, String... values) {
    Row row = sheet.createRow(rowIndex);
    for (int c = 0; c < values.length; c++) {
      row.createCell(c, CellType.STRING).setCellValue(values[c]);
    }
  }

  private static String csv(byte[] xlsx) {
    return new String(XlsxCsvConverter.toCsv(xlsx), StandardCharsets.UTF_8);
  }

  @Test
  @DisplayName("Tiêu đề và dòng dữ liệu thành CSV, kết thúc bằng xuống dòng, không BOM")
  void basicTable() {
    byte[] file =
        xlsx(
            wb -> {
              Sheet s = wb.createSheet("Data");
              text(s, 0, "studentCode", "cohort");
              text(s, 1, "SV001", "K22");
              text(s, 2, "SV002", "K22");
            });

    assertThat(csv(file)).isEqualTo("studentCode,cohort\nSV001,K22\nSV002,K22\n");
  }

  @Test
  @DisplayName("Ô số hiện như Excel: 2110001 không thành 2110001.0; 8.5 và 0.25 giữ dấu chấm")
  void numericCells() {
    byte[] file =
        xlsx(
            wb -> {
              Sheet s = wb.createSheet("Data");
              text(s, 0, "studentCode", "score", "ratio");
              Row r = s.createRow(1);
              r.createCell(0).setCellValue(2110001);
              r.createCell(1).setCellValue(8.5);
              r.createCell(2).setCellValue(0.25);
            });

    assertThat(csv(file)).isEqualTo("studentCode,score,ratio\n2110001,8.5,0.25\n");
  }

  @Test
  @DisplayName("Ô công thức lấy giá trị đã tính lưu trong tệp, không lấy chuỗi công thức")
  void formulaUsesCachedValue() {
    byte[] file =
        xlsx(
            wb -> {
              Sheet s = wb.createSheet("Data");
              text(s, 0, "total");
              s.createRow(1).createCell(0).setCellFormula("1+2");
              wb.getCreationHelper().createFormulaEvaluator().evaluateAll();
            });

    assertThat(csv(file)).isEqualTo("total\n3\n");
  }

  @Test
  @DisplayName(
      "Dòng trống giữ chỗ để số dòng CSV bằng số dòng Excel (dòng 3 trống, dữ liệu ở dòng 4)")
  void blankRowKeepsLineNumbers() {
    byte[] file =
        xlsx(
            wb -> {
              Sheet s = wb.createSheet("Data");
              text(s, 0, "studentCode");
              text(s, 1, "SV001");
              text(s, 3, "SV003");
            });

    assertThat(csv(file)).isEqualTo("studentCode\nSV001\n\nSV003\n");
  }

  @Test
  @DisplayName("Dấu phẩy, nháy kép và xuống dòng trong ô được thoát an toàn")
  void escapesSpecialCharacters() {
    byte[] file =
        xlsx(
            wb -> {
              Sheet s = wb.createSheet("Data");
              text(s, 0, "name", "note");
              text(s, 1, "Nguyễn, Văn A", "nói \"xin chào\"\nxuống dòng");
            });

    assertThat(csv(file))
        .isEqualTo("name,note\n\"Nguyễn, Văn A\",\"nói \"\"xin chào\"\" xuống dòng\"\n");
  }

  @Test
  @DisplayName("Chỉ đọc trang tính đầu tiên")
  void readsFirstSheetOnly() {
    byte[] file =
        xlsx(
            wb -> {
              text(wb.createSheet("First"), 0, "a");
              text(wb.createSheet("Second"), 0, "b");
            });

    assertThat(csv(file)).isEqualTo("a\n");
  }

  @Test
  @DisplayName("Tệp không phải .xlsx thật: báo lỗi 400 dễ hiểu")
  void garbageContent() {
    assertThatThrownBy(
            () -> XlsxCsvConverter.toCsv("không phải excel".getBytes(StandardCharsets.UTF_8)))
        .isInstanceOf(AppException.class)
        .hasMessageContaining("Không đọc được tệp .xlsx");
  }

  @Test
  @DisplayName("Trang tính không có dữ liệu: báo lỗi 400")
  void emptySheet() {
    byte[] file = xlsx(wb -> wb.createSheet("Empty"));

    assertThatThrownBy(() -> XlsxCsvConverter.toCsv(file))
        .isInstanceOf(AppException.class)
        .hasMessageContaining("không có dữ liệu");
  }

  @Test
  @DisplayName("Dữ liệu ở dòng vượt MAX_ROWS bị từ chối")
  void tooManyRows() {
    byte[] file =
        xlsx(
            wb -> {
              Sheet s = wb.createSheet("Data");
              text(s, 0, "a");
              text(s, XlsxCsvConverter.MAX_ROWS, "x");
            });

    assertThatThrownBy(() -> XlsxCsvConverter.toCsv(file))
        .isInstanceOf(AppException.class)
        .hasMessageContaining("vượt quá");
  }

  @Test
  @DisplayName("Dữ liệu ở cột vượt MAX_COLUMNS bị từ chối")
  void tooManyColumns() {
    byte[] file =
        xlsx(
            wb -> {
              Sheet s = wb.createSheet("Data");
              text(s, 0, "a");
              s.getRow(0).createCell(XlsxCsvConverter.MAX_COLUMNS).setCellValue("x");
            });

    assertThatThrownBy(() -> XlsxCsvConverter.toCsv(file))
        .isInstanceOf(AppException.class)
        .hasMessageContaining("cột");
  }

  @Test
  @DisplayName("fromCsv rồi toCsv trả đúng dữ liệu; mã dạng 007 giữ nguyên vì ô là văn bản")
  void templateRoundTrip() {
    String csv = "﻿code,name\r\n007,\"Lập trình, nâng cao\"\r\nB2,Cơ sở dữ liệu\r\n";

    byte[] file = XlsxCsvConverter.fromCsv(csv.getBytes(StandardCharsets.UTF_8));

    assertThat(csv(file)).isEqualTo("code,name\n007,\"Lập trình, nâng cao\"\nB2,Cơ sở dữ liệu\n");
    try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(file))) {
      Cell first = wb.getSheetAt(0).getRow(1).getCell(0);
      assertThat(first.getCellType()).isEqualTo(CellType.STRING);
      assertThat(wb.getSheetAt(0).getRow(0).getCell(0).getCellStyle().getFont().getBold()).isTrue();
    } catch (IOException ex) {
      throw new IllegalStateException(ex);
    }
  }

  @Test
  @DisplayName("Đi qua ScoreCsvParser: số dòng báo lỗi bằng số dòng Excel")
  void feedsExistingScoreParser() {
    byte[] file =
        xlsx(
            wb -> {
              Sheet s = wb.createSheet("Data");
              text(s, 0, "studentCode", "Q1");
              text(s, 1, "SV001", "5");
              text(s, 3, "SV003", "7");
            });

    ScoreCsvParser.Table table = ScoreCsvParser.parse(XlsxCsvConverter.toCsv(file));

    assertThat(table.headers()).containsExactly("studentCode", "Q1");
    assertThat(table.rows()).extracting(ScoreCsvParser.Row::lineNumber).containsExactly(2, 4);
    assertThat(table.rows().get(1).cells()).containsExactly("SV003", "7");
  }

  @Test
  @DisplayName("ImportFiles: .csv giữ nguyên byte; .XLSX viết hoa vẫn nhận ra")
  void importFilesRouting() {
    byte[] csv = "a,b\n1,2\n".getBytes(StandardCharsets.UTF_8);
    byte[] xlsxFile = xlsx(wb -> text(wb.createSheet("Data"), 0, "a", "b"));

    assertThat(ImportFiles.toCsvBytes("data.csv", csv)).isSameAs(csv);
    assertThat(ImportFiles.isXlsx("DATA.XLSX")).isTrue();
    assertThat(new String(ImportFiles.toCsvBytes("DATA.XLSX", xlsxFile), StandardCharsets.UTF_8))
        .isEqualTo("a,b\n");
  }

  @Test
  @DisplayName(
      "ImportFiles: .xls (Excel cũ) bị từ chối kèm hướng dẫn; .xlsx giả (không phải zip) bị từ chối")
  void importFilesRejects() {
    byte[] anything = "x".getBytes(StandardCharsets.UTF_8);

    assertThatThrownBy(() -> ImportFiles.toCsvBytes("old.xls", anything))
        .isInstanceOf(AppException.class)
        .hasMessageContaining(".xls");
    assertThatThrownBy(() -> ImportFiles.toCsvBytes("fake.xlsx", anything))
        .isInstanceOf(AppException.class)
        .hasMessageContaining("không phải tệp Excel");
    assertThatThrownBy(() -> ImportFiles.toCsvBytes("empty.xlsx", new byte[0]))
        .isInstanceOf(AppException.class);
  }

  @Test
  @DisplayName("fromCsv chỉ có dòng tiêu đề vẫn tạo được tệp hợp lệ")
  void fromCsvHeaderOnly() {
    byte[] file = XlsxCsvConverter.fromCsv("a,b\r\n".getBytes(StandardCharsets.UTF_8));

    assertThat(csv(file)).isEqualTo("a,b\n");
  }

  @Test
  @DisplayName("Các tệp .xlsx trong doc/data khớp nội dung với 13 tệp .csv tương ứng")
  void docDataXlsxMatchesCsvFiles() throws IOException {
    Path dataDir = Path.of("doc/data");
    assertThat(dataDir).isDirectory();

    List<Path> csvFiles;
    try (Stream<Path> stream = Files.list(dataDir)) {
      csvFiles =
          stream.filter(p -> String.valueOf(p.getFileName()).endsWith(".csv")).sorted().toList();
    }
    assertThat(csvFiles).hasSize(13);

    for (Path csvPath : csvFiles) {
      String csvName = String.valueOf(csvPath.getFileName());
      Path xlsxPath = dataDir.resolve(csvName.substring(0, csvName.length() - 4) + ".xlsx");
      assertThat(xlsxPath).isRegularFile();

      List<List<String>> expectedRows =
          CsvUtils.parse(new ByteArrayInputStream(Files.readAllBytes(csvPath))).stream()
              .map(row -> row.stream().map(String::trim).toList())
              .toList();
      byte[] convertedCsv = XlsxCsvConverter.toCsv(Files.readAllBytes(xlsxPath));
      List<List<String>> actualRows = CsvUtils.parse(new ByteArrayInputStream(convertedCsv));

      assertThat(actualRows)
          .as("Nội dung %s phải khớp %s", xlsxPath, csvPath)
          .isEqualTo(expectedRows);
    }
  }
}
