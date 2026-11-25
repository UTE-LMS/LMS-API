package com.hcmute.lms.lmsapi.dto;

import com.hcmute.lms.lmsapi.enums.StudentStatus;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentResponse {

  private String id;
  private String studentCode;
  private String fullName;
  private String cohort;
  private StudentStatus status;
  private Instant createdAt;
  private Instant updatedAt;
}
