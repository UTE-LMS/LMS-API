package com.hcmute.lms.lmsapi.document;

import com.hcmute.lms.lmsapi.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * Cặp phân công (vai trò, phạm vi phụ trách) của người dùng.
 * Một người dùng có thể có nhiều phân công vai trò (quyền tổng hợp là phép HỢP - UNION).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleAssignment {

    /**
     * Vai trò được phân công (ADMIN, LECTURER, VIEWER...).
     */
    @Field("role")
    private Role role;

    /**
     * Phạm vi áp dụng cho vai trò này (có thể null nếu vai trò áp dụng toàn hệ thống hoặc chưa gán).
     */
    @Field("scope")
    private UserScope scope;
}
