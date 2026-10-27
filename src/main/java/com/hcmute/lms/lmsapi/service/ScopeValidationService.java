package com.hcmute.lms.lmsapi.service;

import com.hcmute.lms.lmsapi.document.RoleAssignment;
import com.hcmute.lms.lmsapi.document.UserScope;

import java.util.List;

/**
 * Dịch vụ kiểm tra tính hợp lệ của phạm vi phụ trách (Scope Validation Service).
 * Đảm bảo các ID trong programIds, courseIds, sectionIds tồn tại trên CSDL trước khi gán cho người dùng (F01 / US-02).
 */
public interface ScopeValidationService {

    /**
     * Xác thực tính hợp lệ của một UserScope đơn lẻ.
     * Ném BusinessRuleViolationException (HTTP 422) nếu có ID không tồn tại trong CSDL.
     *
     * @param scope đối tượng UserScope cần kiểm tra
     */
    void validateScope(UserScope scope);

    /**
     * Xác thực tính hợp lệ của toàn bộ danh sách RoleAssignment của người dùng.
     * Duyệt qua từng RoleAssignment và kiểm tra UserScope nếu có.
     * Ném BusinessRuleViolationException (HTTP 422) nếu có ID không tồn tại.
     *
     * @param roleAssignments danh sách phân công vai trò cần kiểm tra
     */
    void validateRoleAssignments(List<RoleAssignment> roleAssignments);
}
