package com.hcmute.lms.lmsapi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Thông tin chi tiết về trường dữ liệu không hợp lệ trong Bean Validation.
 * Đặt tại package com.hcmute.lms.lmsapi.dto theo chuẩn SRP.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FieldErrorDetail {

    /**
     * Tên trường bị lỗi (ví dụ: "name", "weight", "items[0].score").
     */
    private String field;

    /**
     * Thông điệp giải thích lỗi (ví dụ: "Tên không được để trống", "Trọng số phải nằm trong khoảng (0, 1]").
     */
    private String message;

    /**
     * Giá trị đầu vào bị từ chối (có thể là null, số, chuỗi...).
     */
    private Object rejectedValue;
}
