package com.hcmute.lms.lmsapi.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

/**
 * Tỷ lệ % đóng góp W_{p,j} của bài kiểm tra p cho chuẩn đầu ra cloId (trong khoảng (0, 1]).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentCloWeight {

  @Field(name = "cloId", targetType = FieldType.OBJECT_ID)
  private String cloId;

  @Field("weight")
  private Double weight;
}
