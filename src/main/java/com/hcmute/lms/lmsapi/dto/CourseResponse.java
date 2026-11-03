package com.hcmute.lms.lmsapi.dto;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseResponse {

  private String id;
  private String code;
  private String name;
  private Integer credits;
  private Map<String, Object> extra;
}
