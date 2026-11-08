package com.hcmute.lms.lmsapi.service.impl;

import com.hcmute.lms.lmsapi.document.AssessmentItem;
import com.hcmute.lms.lmsapi.document.BloomCoverage;
import com.hcmute.lms.lmsapi.document.CloCoverage;
import com.hcmute.lms.lmsapi.document.CoverageSummary;
import com.hcmute.lms.lmsapi.document.OutcomeMapping;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

/**
 * Tính độ phủ của bài đánh giá theo CLO và theo mức Bloom từ items[] (NEW-01/AC-6). Lớp thuần: chỉ
 * đọc danh sách đầu vào, không truy cập CSDL.
 *
 * <p>Cách tính: tổng điểm {@code total = Σ maxScore}. Điểm của một CLO là {@code Σ (maxScore ×
 * weight)} trên các phần tử có đo CLO đó; vì Σ weight của mỗi phần tử bằng 1 nên tổng điểm các CLO
 * bằng {@code total}. Điểm của một mức Bloom là {@code Σ maxScore} các phần tử ở mức đó (phần tử
 * không khai báo Bloom không được tính vào {@code byBloom}). Phần trăm = điểm / total × 100.
 */
@Component
public class AssessmentCoverageCalculator {

  private static final double PERCENT = 100.0;

  public CoverageSummary compute(List<AssessmentItem> items) {
    double total = 0;
    Map<String, Double> pointsByClo = new LinkedHashMap<>();
    Map<Integer, double[]> byBloom = new TreeMap<>(); // [0] = số phần tử, [1] = điểm

    for (AssessmentItem item : items) {
      double maxScore = item.getMaxScore();
      total += maxScore;
      for (OutcomeMapping mapping : item.getOutcomeMappings()) {
        pointsByClo.merge(mapping.getCloId(), maxScore * mapping.getWeight(), Double::sum);
      }
      if (item.getBloomLevel() != null) {
        double[] bucket = byBloom.computeIfAbsent(item.getBloomLevel(), level -> new double[2]);
        bucket[0] += 1;
        bucket[1] += maxScore;
      }
    }

    final double totalPoints = total;
    List<CloCoverage> cloCoverage = new ArrayList<>();
    pointsByClo.forEach(
        (cloId, points) ->
            cloCoverage.add(
                CloCoverage.builder()
                    .cloId(cloId)
                    .points(points)
                    .percentage(percentage(points, totalPoints))
                    .build()));

    List<BloomCoverage> bloomCoverage = new ArrayList<>();
    byBloom.forEach(
        (level, bucket) ->
            bloomCoverage.add(
                BloomCoverage.builder()
                    .bloomLevel(level)
                    .questionCount((int) bucket[0])
                    .points(bucket[1])
                    .percentage(percentage(bucket[1], totalPoints))
                    .build()));

    return CoverageSummary.builder()
        .totalItemsSum(total)
        .byClo(cloCoverage)
        .byBloom(bloomCoverage)
        .build();
  }

  private double percentage(double points, double total) {
    return total > 0 ? points / total * PERCENT : 0.0;
  }
}
