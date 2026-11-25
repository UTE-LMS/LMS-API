package com.hcmute.lms.lmsapi.dto;

import com.hcmute.lms.lmsapi.enums.StudentStatus;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentBatchStatusUpdateResponse {

  private int requestedCount;
  private int updatedCount;
  private StudentStatus status;
  private List<StudentResponse> students;
}
