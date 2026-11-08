package com.hcmute.lms.lmsapi.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

/**
 * CLO mà một phần tử đo và phần điểm của phần tử được chia cho CLO đó. PI không nằm ở đây: PI được
 * suy ra từ kết quả CLO qua clo_pi_links (ADR 0011).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutcomeMapping {

  @Field(name = "cloId", targetType = FieldType.OBJECT_ID)
  private String cloId;

  /** Phần điểm chia cho CLO, trong (0, 1]; tổng của một phần tử bằng 1. */
  @Field("weight")
  private Double weight;
}
