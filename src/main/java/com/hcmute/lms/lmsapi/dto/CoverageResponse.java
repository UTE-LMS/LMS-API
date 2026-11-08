package com.hcmute.lms.lmsapi.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Độ phủ của bài đánh giá theo CLO và mức Bloom (NEW-01/AC-6). {@code percentage} là phần trăm
 * 0-100 so với tổng điểm {@code totalItemsSum}. Phần tử không khai báo mức Bloom không nằm trong
 * {@code byBloom}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoverageResponse {

  private Double totalItemsSum;
  private List<ByClo> byClo;
  private List<ByBloom> byBloom;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ByClo {
    private String cloId;

    /** Mã CLO (CLO1...) để màn hình duyệt dễ đọc; chỉ có ở endpoint /coverage. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String cloCode;

    private Double points;
    private Double percentage;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ByBloom {
    private Integer bloomLevel;
    private Integer questionCount;
    private Double points;
    private Double percentage;
  }
}
