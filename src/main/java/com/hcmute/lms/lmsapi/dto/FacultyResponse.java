package com.hcmute.lms.lmsapi.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FacultyResponse {

  private String id;
  private String code;
  private String name;
  private String description;
  private boolean isActive;
  private long programManagerCount;
  private Instant createdAt;
  private Instant updatedAt;
}
