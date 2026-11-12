package com.hcmute.lms.lmsapi.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.hcmute.lms.lmsapi.document.AssessmentItem;
import com.hcmute.lms.lmsapi.document.BloomCoverage;
import com.hcmute.lms.lmsapi.document.CloCoverage;
import com.hcmute.lms.lmsapi.document.CoverageSummary;
import com.hcmute.lms.lmsapi.document.OutcomeMapping;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AssessmentCoverageCalculator: độ phủ theo CLO và Bloom (v0.7)")
class AssessmentCoverageCalculatorTest {

  private static final String CLO1 = "671dcba5f123456789abc001";
  private static final String CLO2 = "671dcba5f123456789abc002";

  private final AssessmentCoverageCalculator calculator = new AssessmentCoverageCalculator();

  private AssessmentItem item(Integer bloom, OutcomeMapping... mappings) {
    return AssessmentItem.builder()
        .code("I")
        .bloomLevel(bloom)
        .outcomeMappings(List.of(mappings))
        .build();
  }

  private OutcomeMapping map(String cloId, double maxScore) {
    return OutcomeMapping.builder().cloId(cloId).maxScore(maxScore).build();
  }

  @Test
  @DisplayName("Ví dụ minh họa tính tay: byClo và byBloom khớp")
  void handComputedExample() {
    CoverageSummary summary =
        calculator.compute(
            List.of(
                item(1, map(CLO1, 4.0)),
                item(3, map(CLO1, 3.0), map(CLO2, 3.0)),
                item(null, map(CLO2, 10.0))));

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
  @DisplayName("Tổng điểm các CLO luôn bằng tổng Σ maxScore")
  void cloPointsSumToTotal() {
    CoverageSummary summary =
        calculator.compute(
            List.of(
                item(2, map(CLO1, 0.625), map(CLO2, 1.875)),
                item(2, map(CLO1, 4.5), map(CLO2, 3.0))));

    double cloSum = summary.getByClo().stream().mapToDouble(CloCoverage::getPoints).sum();
    double percentSum = summary.getByClo().stream().mapToDouble(CloCoverage::getPercentage).sum();
    assertThat(cloSum).isCloseTo(summary.getTotalItemsSum(), within(1e-9));
    assertThat(percentSum).isCloseTo(100.0, within(1e-9));
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
