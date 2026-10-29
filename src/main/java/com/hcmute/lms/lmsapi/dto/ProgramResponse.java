package com.hcmute.lms.lmsapi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProgramResponse {

    private String id;
    private String code;
    private String name;
    private String level;
    private String degree;
}
