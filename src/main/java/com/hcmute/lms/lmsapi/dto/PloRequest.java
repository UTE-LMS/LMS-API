package com.hcmute.lms.lmsapi.dto;

import com.hcmute.lms.lmsapi.enums.PloCategory;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PloRequest {

    @NotBlank(message = "Mã PLO không được để trống")
    @Pattern(regexp = "^PLO[1-9][0-9]*$", message = "Mã PLO phải có dạng PLO1, PLO2, ...")
    private String code;

    @NotNull(message = "Thứ tự hiển thị không được để trống")
    @PositiveOrZero(message = "Thứ tự hiển thị không được âm")
    private Integer displayOrder;

    private PloCategory category;

    @Size(max = 5000, message = "Nội dung gốc tối đa 5000 ký tự")
    private String contentOriginal;

    @Size(max = 5000, message = "Nội dung diễn giải tối đa 5000 ký tự")
    private String contentSummary;

    @Min(value = 1, message = "Mức năng lực phải từ 1 đến 6")
    @Max(value = 6, message = "Mức năng lực phải từ 1 đến 6")
    private Integer capabilityLevelRaw;

    @Size(max = 2000, message = "Ghi chú rà soát tối đa 2000 ký tự")
    private String reviewNote;
}
