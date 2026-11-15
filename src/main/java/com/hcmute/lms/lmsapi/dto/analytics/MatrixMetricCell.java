package com.hcmute.lms.lmsapi.dto.analytics;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Dữ liệu chỉ số của từng ô giao nhau (cell) trong ma trận phân tích đa chiều (Milestone 1). Tuân
 * thủ bất biến 10 trường chỉ số và xử lý mẫu số bằng 0 (trả về null thay vì 0.0 hoặc 0%).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatrixMetricCell {

  /** Số lượng sinh viên ghi danh / thuộc phạm vi khảo sát. */
  private Integer enrolled;

  /** Số lượng sinh viên được đánh giá hợp lệ (evaluated = passed + failed). */
  private Integer evaluated;

  /** Số lượng sinh viên đạt chuẩn (passed). */
  private Integer passed;

  /** Số lượng sinh viên chưa đạt chuẩn (failed). */
  private Integer failed;

  /** Số lượng sinh viên thiếu dữ liệu điểm (insufficientData = max(0, enrolled - evaluated)). */
  private Integer insufficientData;

  /**
   * Điểm trung bình chuẩn hóa [0, 1] trên tập sinh viên được đánh giá. Bằng null khi evaluated ==
   * 0.
   */
  private Double mean;

  /**
   * Tỷ lệ đạt trên tập sinh viên được đánh giá (passed / evaluated). Bằng null khi evaluated == 0.
   */
  private Double passRate;

  /** Tỷ lệ đạt trên toàn bộ sinh viên ghi danh (passed / enrolled). Bằng null khi enrolled == 0. */
  private Double passRateOfEnrolled;

  /** Trạng thái lượt tính chính thức hay thử nghiệm. */
  @JsonProperty("isOfficial")
  @Builder.Default
  private Boolean isOfficial = false;

  /** Đánh dấu dữ liệu đã bị cũ do điểm số hoặc cấu hình thay đổi sau lượt tính. */
  @JsonProperty("isStale")
  @Builder.Default
  private Boolean isStale = false;

  /**
   * Tạo một ô chỉ số rỗng (không có dữ liệu đánh giá), bảo toàn quy tắc mẫu số bằng 0: các tỷ lệ và
   * điểm trung bình đều là null.
   */
  public static MatrixMetricCell empty(boolean isOfficial, boolean isStale) {
    return MatrixMetricCell.builder()
        .enrolled(0)
        .evaluated(0)
        .passed(0)
        .failed(0)
        .insufficientData(0)
        .mean(null)
        .passRate(null)
        .passRateOfEnrolled(null)
        .isOfficial(isOfficial)
        .isStale(isStale)
        .build();
  }

  /**
   * Tính toán và khởi tạo ô chỉ số an toàn từ các số đếm nguyên thủy, tuân thủ nghiêm ngặt quy tắc
   * xử lý mẫu số bằng 0.
   */
  public static MatrixMetricCell calculate(
      int enrolled,
      int passed,
      int failed,
      Double mean,
      Double overridePassRate,
      boolean isOfficial,
      boolean isStale) {
    int evaluated = passed + failed;
    int insufficientData = Math.max(0, enrolled - evaluated);

    Double computedPassRate;
    Double computedMean;
    if (evaluated <= 0) {
      computedPassRate = null;
      computedMean = null;
    } else {
      if (overridePassRate != null) {
        computedPassRate = overridePassRate;
      } else {
        computedPassRate = (double) passed / (double) evaluated;
      }
      computedMean = mean;
    }

    Double computedPassRateOfEnrolled;
    if (enrolled <= 0 || evaluated <= 0) {
      computedPassRateOfEnrolled = null;
    } else {
      computedPassRateOfEnrolled = (double) passed / (double) enrolled;
    }

    return MatrixMetricCell.builder()
        .enrolled(enrolled)
        .evaluated(evaluated)
        .passed(passed)
        .failed(failed)
        .insufficientData(insufficientData)
        .mean(computedMean)
        .passRate(computedPassRate)
        .passRateOfEnrolled(computedPassRateOfEnrolled)
        .isOfficial(isOfficial)
        .isStale(isStale)
        .build();
  }
}
