package com.hcmute.lms.lmsapi.dto;

import com.hcmute.lms.lmsapi.enums.PiContributionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoursePiContributionResponse {

    private String id;
    private String programVersionId;
    private String courseId;
    private String piId;
    private PiContributionType contribution;
    private VerificationResponse verification;
}
