package com.hcmute.lms.lmsapi.dto;

import com.hcmute.lms.lmsapi.enums.VerificationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationResponse {

    private VerificationStatus status;
    private String verifiedBy;
    private Instant verifiedAt;
    private String note;
}
