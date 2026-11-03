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
public class TermUpdateRequest {

  private String academicYear;

  private Instant startDate;

  private Instant endDate;
}
