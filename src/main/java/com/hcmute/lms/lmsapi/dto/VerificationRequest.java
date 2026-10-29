package com.hcmute.lms.lmsapi.dto;

import com.hcmute.lms.lmsapi.enums.VerificationStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationRequest {

    @NotNull(message = "Trạng thái xác nhận không được để trống")
    private VerificationStatus status;

    @Size(max = 2000, message = "Ghi chú xác nhận tối đa 2000 ký tự")
    private String note;
}
