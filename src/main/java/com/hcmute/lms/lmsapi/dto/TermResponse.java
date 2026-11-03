package com.hcmute.lms.lmsapi.dto;

import com.hcmute.lms.lmsapi.enums.TermStatus;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TermResponse {

  private String id;
  private String code;
  private String academicYear;
  private Instant startDate;
  private Instant endDate;
  private TermStatus status;
}
