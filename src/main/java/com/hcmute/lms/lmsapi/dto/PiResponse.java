package com.hcmute.lms.lmsapi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PiResponse {

    private String id;
    private String programVersionId;
    private String ploId;
    private String code;
    private Integer displayOrder;
    private String nameOriginalTerm;
    private String contentOriginal;
    private String contentShort;
    private Integer capabilityLevelRaw;
    private Double targetPassRate;
    private VerificationResponse verification;
    private String reviewNote;
}
