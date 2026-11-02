package com.hcmute.lms.lmsapi.dto;

import com.hcmute.lms.lmsapi.enums.ProgramVersionStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
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
public class ProgramVersionUpdateRequest {

    @NotBlank(message = "Phiên bản chương trình đào tạo không được để trống")
    @Size(max = 100, message = "Phiên bản tối đa 100 ký tự")
    private String version;

    @PositiveOrZero(message = "Tổng số tín chỉ không được âm")
    private Integer totalCredits;

    @DecimalMin(value = "0.0", message = "Thời gian đào tạo không được âm")
    private Double durationYears;

    @Builder.Default
    private List<@Size(max = 50, message = "Mỗi khóa tối đa 50 ký tự") String> applicableCohorts = new ArrayList<>();

    private ProgramVersionStatus status;
}
