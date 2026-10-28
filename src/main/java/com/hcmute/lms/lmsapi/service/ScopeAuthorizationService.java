package com.hcmute.lms.lmsapi.service;

import com.hcmute.lms.lmsapi.enums.AccessType;
import com.hcmute.lms.lmsapi.dto.UserPrincipal;

/**
 * Service kiểm tra phân quyền truy cập tài nguyên lớp học phần theo vai trò và phạm vi (F01 / US-03 / ADR 0003).
 * Áp dụng ma trận phân quyền vai trò × phạm vi × READ/WRITE, quy tắc lecturerIds độc lập,
 * định nghĩa scope rỗng nghiêm ngặt và nguyên lý fail-closed.
 */
public interface ScopeAuthorizationService {

    /**
     * Kiểm tra người dùng có quyền truy cập (READ hoặc WRITE) trên lớp học phần tương ứng hay không.
     *
     * @param principal  thông tin người dùng xác thực trong Security Context
     * @param sectionId  định danh lớp học phần (MongoDB ObjectId dạng chuỗi)
     * @param accessType loại hành vi truy cập (READ hoặc WRITE)
     * @return {@code true} nếu người dùng được cấp quyền truy cập; {@code false} nếu bị từ chối (fail-closed)
     */
    boolean canAccessSection(UserPrincipal principal, String sectionId, AccessType accessType);
}
