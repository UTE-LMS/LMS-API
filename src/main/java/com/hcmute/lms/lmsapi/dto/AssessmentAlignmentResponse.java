package com.hcmute.lms.lmsapi.dto;

import com.hcmute.lms.lmsapi.enums.AssessmentStatus;
import com.hcmute.lms.lmsapi.enums.AssessmentType;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Báo cáo độ bám sát chuẩn đầu ra của bộ đề trong một lớp học phần: mỗi CLO của đề cương được hỏi ở
 * những bài nào, chiếm bao nhiêu điểm / tỷ trọng, mức Bloom của câu hỏi so với mức Bloom của CLO,
 * và CLO đó đóng góp vào PI/PLO nào. Chỉ đọc từ ma trận đề ({@code assessments}), không có nội dung
 * câu hỏi.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentAlignmentResponse {

  private String sectionId;
  private String sectionCode;
  private String courseId;
  private String syllabusId;

  /** Số bài đánh giá của lớp được đưa vào đối chiếu. */
  private int assessmentCount;

  /** Tổng điểm tối đa của mọi cặp (câu hỏi, CLO) trong các bài (mẫu số của {@code percentage}). */
  private double totalPoints;

  @Builder.Default private List<CloAlignment> clos = new ArrayList<>();

  /** Số CLO của đề cương có cảnh báo (để màn hình hiển thị nhanh). */
  private int cloWithWarningCount;

  /** Mã cảnh báo trên từng CLO. */
  public static final String NOT_ASSESSED = "NOT_ASSESSED";

  public static final String WEIGHT_NOT_FULL = "WEIGHT_NOT_FULL";
  public static final String BLOOM_BELOW_CLO = "BLOOM_BELOW_CLO";
  public static final String BLOOM_UNDECLARED = "BLOOM_UNDECLARED";

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class CloAlignment {
    private String cloId;
    private String cloCode;

    /** Mức Bloom mục tiêu của CLO trong đề cương (có thể null). */
    private Integer cloBloomLevel;

    /** Tổng điểm tối đa của CLO này qua mọi bài của lớp. */
    private double points;

    /** Phần trăm 0-100 so với {@code totalPoints}. */
    private Double percentage;

    /** Tổng tỷ lệ đóng góp Σ W(p,j) của các bài cho CLO (cần bằng 1.0 khi tổng hợp học phần). */
    private double totalWeight;

    /** Mức Bloom cao nhất trong các câu hỏi đánh giá CLO (null nếu không khai báo). */
    private Integer maxItemBloomLevel;

    @Builder.Default private List<AssessmentUsage> assessments = new ArrayList<>();
    @Builder.Default private List<BloomUsage> itemBloom = new ArrayList<>();
    @Builder.Default private List<PiContribution> linkedPis = new ArrayList<>();
    @Builder.Default private List<String> warnings = new ArrayList<>();
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class AssessmentUsage {
    private String assessmentId;
    private String assessmentCode;
    private AssessmentType type;
    private AssessmentStatus status;
    private int questionCount;
    private double points;

    /** Tỷ lệ đóng góp W(p,j) của bài này cho CLO (0 nếu chưa khai báo). */
    private double weight;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class BloomUsage {
    private Integer bloomLevel;
    private int questionCount;
    private double points;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class PiContribution {
    private String piId;
    private String piCode;
    private String ploId;
    private String ploCode;

    /** Trọng số W(CLO→PI) khai báo trong clo_pi_links. */
    private Double weight;
  }
}
