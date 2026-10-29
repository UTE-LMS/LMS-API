package com.hcmute.lms.lmsapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProgramRequest {

    @NotBlank(message = "Mã chương trình đào tạo không được để trống")
    @Size(max = 64, message = "Mã chương trình đào tạo tối đa 64 ký tự")
    private String code;

    @NotBlank(message = "Tên chương trình đào tạo không được để trống")
    @Size(max = 255, message = "Tên chương trình đào tạo tối đa 255 ký tự")
    private String name;

    @Size(max = 100, message = "Trình độ tối đa 100 ký tự")
    private String level;

    @Size(max = 100, message = "Bằng cấp tối đa 100 ký tự")
    private String degree;
}
