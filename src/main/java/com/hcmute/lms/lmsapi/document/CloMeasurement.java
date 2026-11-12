package com.hcmute.lms.lmsapi.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CloMeasurement {

  @Field("defaultPassThreshold")
  private Double defaultPassThreshold;
}
