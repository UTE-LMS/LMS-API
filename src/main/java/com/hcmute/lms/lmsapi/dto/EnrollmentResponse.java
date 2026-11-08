package com.hcmute.lms.lmsapi.dto;

import com.hcmute.lms.lmsapi.enums.EnrollmentStatus;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnrollmentResponse {

  private String id;
  private String sectionId;
  private String studentId;
  private String studentRef;
  private String cohort;
  private String majorCode;
  private EnrollmentStatus status;
  private Instant enrolledAt;
}
