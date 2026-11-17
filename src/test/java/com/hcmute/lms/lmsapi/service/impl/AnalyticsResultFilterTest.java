package com.hcmute.lms.lmsapi.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.hcmute.lms.lmsapi.dto.analytics.AnalyticsMatrixResponse;
import com.hcmute.lms.lmsapi.dto.analytics.AnalyticsQueryRequest;
import com.hcmute.lms.lmsapi.dto.analytics.MatrixHeader;
import com.hcmute.lms.lmsapi.dto.analytics.MatrixMetricCell;
import com.hcmute.lms.lmsapi.enums.AnalyticsDimension;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AnalyticsServiceImpl.applyResultFilters")
class AnalyticsResultFilterTest {

  private static MatrixHeader header(String id, String code) {
    return MatrixHeader.builder().id(id).code(code).name(code).build();
  }

  private static MatrixMetricCell cell(int passed, int failed) {
    return MatrixMetricCell.calculate(passed + failed, passed, failed, 0.5, null, true, false);
  }

  /** Dòng PLO1, PLO2; cột SV1 (đạt PLO1, rớt PLO2), SV2 (đạt cả hai), SV3 (rớt cả hai). */
  private static AnalyticsMatrixResponse sample() {
    List<List<MatrixMetricCell>> matrix = new ArrayList<>();
    matrix.add(new ArrayList<>(List.of(cell(1, 0), cell(1, 0), cell(0, 1))));
    matrix.add(new ArrayList<>(List.of(cell(0, 1), cell(1, 0), cell(0, 1))));
    return AnalyticsMatrixResponse.builder()
        .dimension(AnalyticsDimension.STUDENT)
        .rows(new ArrayList<>(List.of(header("r1", "PLO1"), header("r2", "PLO2"))))
        .columns(
            new ArrayList<>(List.of(header("s1", "SV1"), header("s2", "SV2"), header("s3", "SV3"))))
        .matrix(matrix)
        .build();
  }

  private static AnalyticsQueryRequest request(List<String> subjectIds, Boolean passed) {
    return AnalyticsQueryRequest.builder()
        .dimension(AnalyticsDimension.STUDENT)
        .subjectIds(subjectIds)
        .passed(passed)
        .build();
  }

  @Test
  @DisplayName("không truyền filter: giữ nguyên ma trận")
  void noFilterKeepsMatrix() {
    AnalyticsMatrixResponse result =
        AnalyticsServiceImpl.applyResultFilters(sample(), request(null, null));
    assertThat(result.getRows()).hasSize(2);
    assertThat(result.getColumns()).hasSize(3);
  }

  @Test
  @DisplayName("subjectIds theo mã: chỉ giữ dòng PLO2, ô không đổi")
  void filterBySubjectCode() {
    AnalyticsMatrixResponse result =
        AnalyticsServiceImpl.applyResultFilters(sample(), request(List.of("PLO2"), null));
    assertThat(result.getRows()).extracting(MatrixHeader::getCode).containsExactly("PLO2");
    assertThat(result.getColumns()).hasSize(3);
    assertThat(result.getMatrix()).hasSize(1);
    assertThat(result.getMatrix().get(0).get(0).getFailed()).isEqualTo(1);
  }

  @Test
  @DisplayName("subjectIds theo id cũng khớp")
  void filterBySubjectId() {
    AnalyticsMatrixResponse result =
        AnalyticsServiceImpl.applyResultFilters(sample(), request(List.of("r1"), null));
    assertThat(result.getRows()).extracting(MatrixHeader::getCode).containsExactly("PLO1");
  }

  @Test
  @DisplayName("passed=false: giữ cột có người Chưa đạt, bỏ cột toàn Đạt")
  void filterFailedDropsAllPassedColumns() {
    AnalyticsMatrixResponse result =
        AnalyticsServiceImpl.applyResultFilters(sample(), request(null, false));
    assertThat(result.getColumns()).extracting(MatrixHeader::getCode).containsExactly("SV1", "SV3");
    assertThat(result.getRows()).hasSize(2);
    assertThat(result.getMatrix().get(0)).hasSize(2);
  }

  @Test
  @DisplayName("passed=true kết hợp subjectIds: PLO2 chỉ còn SV2 đạt")
  void filterPassedAndSubject() {
    AnalyticsMatrixResponse result =
        AnalyticsServiceImpl.applyResultFilters(sample(), request(List.of("PLO2"), true));
    assertThat(result.getRows()).extracting(MatrixHeader::getCode).containsExactly("PLO2");
    assertThat(result.getColumns()).extracting(MatrixHeader::getCode).containsExactly("SV2");
  }

  @Test
  @DisplayName("không dòng nào khớp: trả về ma trận rỗng, không ném lỗi")
  void noMatchReturnsEmpty() {
    AnalyticsMatrixResponse result =
        AnalyticsServiceImpl.applyResultFilters(sample(), request(List.of("PLO9"), null));
    assertThat(result.getRows()).isEmpty();
    assertThat(result.getMatrix()).isEmpty();
  }

  @Test
  @DisplayName("chiều CURRICULUM_CLASSIFICATION bỏ qua subjectIds")
  void classificationIgnoresSubjectIds() {
    AnalyticsQueryRequest req =
        AnalyticsQueryRequest.builder()
            .dimension(AnalyticsDimension.CURRICULUM_CLASSIFICATION)
            .subjectIds(List.of("PLO9"))
            .build();
    AnalyticsMatrixResponse result = AnalyticsServiceImpl.applyResultFilters(sample(), req);
    assertThat(result.getRows()).hasSize(2);
  }
}
