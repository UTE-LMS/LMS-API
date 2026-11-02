package com.hcmute.lms.lmsapi.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
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
public class PiRequest {

    @NotBlank(message = "ID PLO không được để trống")
    @Pattern(regexp = "^[0-9a-fA-F]{24}$", message = "ID PLO phải là ObjectId 24 ký tự hex")
    private String ploId;

    @NotBlank(message = "Mã PI không được để trống")
    @Pattern(regexp = "^PI[1-9][0-9]*\\.[1-9][0-9]*$", message = "Mã PI phải có dạng PI1.1, PI2.3, ...")
    private String code;

    @NotNull(message = "Thứ tự hiển thị không được để trống")
    @PositiveOrZero(message = "Thứ tự hiển thị không được âm")
    private Integer displayOrder;

    @Size(max = 255, message = "Tên gốc tối đa 255 ký tự")
    private String nameOriginalTerm;

    @Size(max = 5000, message = "Nội dung gốc tối đa 5000 ký tự")
    private String contentOriginal;

    @Size(max = 2000, message = "Nội dung rút gọn tối đa 2000 ký tự")
    private String contentShort;

    @Min(value = 1, message = "Mức năng lực phải từ 1 đến 6")
    @Max(value = 6, message = "Mức năng lực phải từ 1 đến 6")
    private Integer capabilityLevelRaw;

    @DecimalMin(value = "0.0", message = "Tỷ lệ đạt mục tiêu phải từ 0 đến 1")
    @DecimalMax(value = "1.0", message = "Tỷ lệ đạt mục tiêu phải từ 0 đến 1")
    private Double targetPassRate;

    @Size(max = 2000, message = "Ghi chú rà soát tối đa 2000 ký tự")
    private String reviewNote;
}
