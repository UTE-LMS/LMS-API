package com.hcmute.lms.lmsapi.dto;

import com.hcmute.lms.lmsapi.enums.ProgramVersionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProgramVersionResponse {

    private String id;
    private String programId;
    private String version;
    private Integer totalCredits;
    private Double durationYears;
    @Builder.Default
    private List<String> applicableCohorts = new ArrayList<>();
    private ProgramVersionStatus status;
    private VerificationResponse verification;
}
