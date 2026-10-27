package com.hcmute.lms.lmsapi.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Định danh đối tượng/collection chịu tác động trong nhật ký kiểm toán.
 * Khớp với chỉ mục: { 'entity.collection': 1, 'entity.id': 1, at: -1 }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEntity {

    /** Tên collection (ví dụ: "scores", "assessments", "measurement_rules", "terms") */
    private String collection;

    /** ID của bản ghi mục tiêu (chuỗi biểu diễn ObjectId hoặc mã) */
    private String id;
}
