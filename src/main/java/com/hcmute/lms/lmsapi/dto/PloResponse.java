package com.hcmute.lms.lmsapi.dto;

import com.hcmute.lms.lmsapi.enums.PloCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PloResponse {

    private String id;
    private String programVersionId;
    private String code;
    private Integer displayOrder;
    private PloCategory category;
    private String contentOriginal;
    private String contentSummary;
    private Integer capabilityLevelRaw;
    private VerificationResponse verification;
    private String reviewNote;
}
