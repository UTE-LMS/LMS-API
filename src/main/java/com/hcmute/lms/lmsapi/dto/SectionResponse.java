package com.hcmute.lms.lmsapi.dto;

import com.hcmute.lms.lmsapi.enums.SectionStatus;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SectionResponse {

  private String id;
  private String code;
  private String courseId;
  private String syllabusId;
  private String termId;
  private List<String> lecturerIds;
  private Integer enrolledCount;
  private SectionStatus status;
}
