package com.hcmute.lms.lmsapi.dto.analytics;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hcmute.lms.lmsapi.enums.AnalyticsDimension;
import com.hcmute.lms.lmsapi.enums.ResultLevel;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Phản hồi kết quả ma trận phân tích đa chiều (Milestone 1). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsMatrixResponse {

  /** Chiều phân tích đã thực hiện. */
  private AnalyticsDimension dimension;

  /** Cấp độ kết quả đo lường được tổng hợp trong ma trận. */
  private ResultLevel level;

  /** Định danh lượt tính toán nguồn (CalculationRun có status SUCCESS). */
  private String runId;

  /** Có phải là lượt tính chính thức không. */
  @JsonProperty("isOfficial")
  @Builder.Default
  private Boolean isOfficial = false;

  /** Đánh dấu dữ liệu của lượt tính đã bị cũ hay không. */
  @JsonProperty("isStale")
  @Builder.Default
  private Boolean isStale = false;

  /** Danh sách tiêu đề các cột của ma trận (ví dụ: các lớp song song, các học kỳ, các khóa). */
  @Builder.Default private List<MatrixHeader> columns = new ArrayList<>();

  /** Danh sách tiêu đề các dòng của ma trận (ví dụ: các CLO, PI, PLO, hoặc nhóm danh mục). */
  @Builder.Default private List<MatrixHeader> rows = new ArrayList<>();

  /**
   * Ma trận 2 chiều chứa các ô chỉ số. Phần tử [i][j] tương ứng với dòng rows[i] và cột columns[j].
   */
  @Builder.Default private List<List<MatrixMetricCell>> matrix = new ArrayList<>();

  /** Chỉ số trang hiện tại (0-indexed) khi phân trang cột sinh viên (dimension = STUDENT). */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Integer page;

  /** Kích thước trang thực tế khi phân trang cột sinh viên (dimension = STUDENT). */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Integer size;

  /** Tổng số sinh viên khớp điều kiện (dimension = STUDENT). */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Long totalElements;

  /** Tổng số trang sinh viên (dimension = STUDENT). */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Integer totalPages;

  /** Cờ trang cuối cùng (dimension = STUDENT). */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Boolean last;
}
