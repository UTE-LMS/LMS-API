package com.hcmute.lms.lmsapi.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.hcmute.lms.lmsapi.document.AssessmentItem;
import com.hcmute.lms.lmsapi.document.BloomCoverage;
import com.hcmute.lms.lmsapi.document.CloCoverage;
import com.hcmute.lms.lmsapi.document.CoverageSummary;
import com.hcmute.lms.lmsapi.document.OutcomeMapping;
import com.hcmute.lms.lmsapi.enums.ItemKind;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AssessmentCoverageCalculator (NEW-01/AC-6): độ phủ theo CLO và Bloom")
class AssessmentCoverageCalculatorTest {

  private static final String CLO1 = "671dcba5f123456789abc001";
  private static final String CLO2 = "671dcba5f123456789abc002";

  private final AssessmentCoverageCalculator calculator = new AssessmentCoverageCalculator();

  private AssessmentItem item(double maxScore, Integer bloom, OutcomeMapping... mappings) {
    return AssessmentItem.builder()
        .code("I")
        .kind(ItemKind.QUESTION)
        .maxScore(maxScore)
        .bloomLevel(bloom)
        .outcomeMappings(List.of(mappings))
        .build();
  }

  private OutcomeMapping map(String cloId, double weight) {
    return OutcomeMapping.builder().cloId(cloId).weight(weight).build();
  }

  /**
   * Ví dụ minh họa tính tay (không lấy từ kết quả của mã): Q1 4 điểm, Bloom 1, đo CLO1; Q2 6 điểm,
   * Bloom 3, chia CLO1 và CLO2 mỗi CLO 0,5; Q3 10 điểm, không khai báo Bloom, đo CLO2. Tổng = 20.
   * CLO1 = 4 + 6 × 0,5 = 7 (35%); CLO2 = 6 × 0,5 + 10 = 13 (65%). Bloom 1: 1 phần tử, 4 điểm (20%);
   * Bloom 3: 1 phần tử, 6 điểm (30%); Q3 không nằm trong byBloom.
   */
  @Test
  @DisplayName("Ví dụ minh họa tính tay: byClo và byBloom khớp")
  void handComputedExample() {
    CoverageSummary summary =
        calculator.compute(
            List.of(
                item(4, 1, map(CLO1, 1.0)),
                item(6, 3, map(CLO1, 0.5), map(CLO2, 0.5)),
                item(10, null, map(CLO2, 1.0))));

    assertThat(summary.getTotalItemsSum()).isEqualTo(20.0);

    assertThat(summary.getByClo()).extracting(CloCoverage::getCloId).containsExactly(CLO1, CLO2);
    assertThat(summary.getByClo().get(0).getPoints()).isCloseTo(7.0, within(1e-9));
    assertThat(summary.getByClo().get(0).getPercentage()).isCloseTo(35.0, within(1e-9));
    assertThat(summary.getByClo().get(1).getPoints()).isCloseTo(13.0, within(1e-9));
    assertThat(summary.getByClo().get(1).getPercentage()).isCloseTo(65.0, within(1e-9));

    assertThat(summary.getByBloom()).extracting(BloomCoverage::getBloomLevel).containsExactly(1, 3);
    assertThat(summary.getByBloom().get(0).getQuestionCount()).isEqualTo(1);
    assertThat(summary.getByBloom().get(0).getPoints()).isEqualTo(4.0);
    assertThat(summary.getByBloom().get(0).getPercentage()).isCloseTo(20.0, within(1e-9));
    assertThat(summary.getByBloom().get(1).getPoints()).isEqualTo(6.0);
    assertThat(summary.getByBloom().get(1).getPercentage()).isCloseTo(30.0, within(1e-9));
  }

  @Test
  @DisplayName("Tổng điểm các CLO luôn bằng tổng Σ maxScore vì Σ weight của mỗi phần tử bằng 1")
  void cloPointsSumToTotal() {
    CoverageSummary summary =
        calculator.compute(
            List.of(
                item(2.5, 2, map(CLO1, 0.25), map(CLO2, 0.75)),
                item(7.5, 2, map(CLO1, 0.6), map(CLO2, 0.4))));

    double cloSum = summary.getByClo().stream().mapToDouble(CloCoverage::getPoints).sum();
    double percentSum = summary.getByClo().stream().mapToDouble(CloCoverage::getPercentage).sum();
    assertThat(cloSum).isCloseTo(summary.getTotalItemsSum(), within(1e-9));
    assertThat(percentSum).isCloseTo(100.0, within(1e-9));
    // Hai phần tử cùng mức Bloom 2 gộp thành một dòng
    assertThat(summary.getByBloom()).hasSize(1);
    assertThat(summary.getByBloom().get(0).getQuestionCount()).isEqualTo(2);
  }

  @Test
  @DisplayName("Bài chưa có phần tử nào: tổng 0, không chia cho 0")
  void emptyItems() {
    CoverageSummary summary = calculator.compute(List.of());

    assertThat(summary.getTotalItemsSum()).isZero();
    assertThat(summary.getByClo()).isEmpty();
    assertThat(summary.getByBloom()).isEmpty();
  }
}
