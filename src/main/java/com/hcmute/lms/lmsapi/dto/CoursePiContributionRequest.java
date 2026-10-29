package com.hcmute.lms.lmsapi.dto;

import com.hcmute.lms.lmsapi.enums.PiContributionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoursePiContributionRequest {

    @NotBlank(message = "ID học phần không được để trống")
    @Pattern(regexp = "^[0-9a-fA-F]{24}$", message = "ID học phần phải là ObjectId 24 ký tự hex")
    private String courseId;

    @NotBlank(message = "ID PI không được để trống")
    @Pattern(regexp = "^[0-9a-fA-F]{24}$", message = "ID PI phải là ObjectId 24 ký tự hex")
    private String piId;

    @NotNull(message = "Mức đóng góp không được để trống")
    private PiContributionType contribution;
}
