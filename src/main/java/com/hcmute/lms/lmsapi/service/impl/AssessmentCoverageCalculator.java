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
 * Tính độ phủ của bài đánh giá theo CLO và theo mức Bloom từ items[] (ADR 0014, Schema v0.7).
 */
@Component
public class AssessmentCoverageCalculator {

  private static final double PERCENT = 100.0;

  public CoverageSummary compute(List<AssessmentItem> items) {
    if (items == null || items.isEmpty()) {
      return CoverageSummary.builder()
          .totalItemsSum(0.0)
          .byClo(new ArrayList<>())
          .byBloom(new ArrayList<>())
          .build();
    }
    double total = 0;
    Map<String, Double> pointsByClo = new LinkedHashMap<>();
    Map<Integer, double[]> byBloom = new TreeMap<>(); // [0] = số phần tử, [1] = điểm

    for (AssessmentItem item : items) {
      double itemMaxScore = 0.0;
      if (item.getOutcomeMappings() != null) {
        for (OutcomeMapping mapping : item.getOutcomeMappings()) {
          double score = mapping.getMaxScore() != null ? mapping.getMaxScore() : 0.0;
          itemMaxScore += score;
          pointsByClo.merge(mapping.getCloId(), score, Double::sum);
        }
      }
      total += itemMaxScore;
      if (item.getBloomLevel() != null) {
        double[] bucket = byBloom.computeIfAbsent(item.getBloomLevel(), level -> new double[2]);
        bucket[0] += 1;
        bucket[1] += itemMaxScore;
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
