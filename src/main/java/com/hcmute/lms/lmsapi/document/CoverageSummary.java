package com.hcmute.lms.lmsapi.document;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * Độ phủ của bài đánh giá theo CLO và theo mức Bloom, tính từ items[] để màn hình duyệt đọc nhanh.
 * {@code percentage} là phần trăm 0-100 so với tổng điểm Σ maxScore.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoverageSummary {

  @Field("totalItemsSum")
  private Double totalItemsSum;

  @Builder.Default
  @Field("byClo")
  private List<CloCoverage> byClo = new ArrayList<>();

  @Builder.Default
  @Field("byBloom")
  private List<BloomCoverage> byBloom = new ArrayList<>();
}
