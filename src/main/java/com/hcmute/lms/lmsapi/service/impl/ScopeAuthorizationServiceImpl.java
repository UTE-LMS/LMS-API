package com.hcmute.lms.lmsapi.service.impl;

import com.hcmute.lms.lmsapi.document.CourseSection;
import com.hcmute.lms.lmsapi.document.Role;
import com.hcmute.lms.lmsapi.document.RoleAssignment;
import com.hcmute.lms.lmsapi.document.UserScope;
import com.hcmute.lms.lmsapi.dto.AccessType;
import com.hcmute.lms.lmsapi.dto.UserPrincipal;
import com.hcmute.lms.lmsapi.repository.CourseSectionRepository;
import com.hcmute.lms.lmsapi.service.ScopeAuthorizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Hiện thực dịch vụ phân quyền phạm vi truy cập tài nguyên lớp học phần (F01 / US-03 / ADR 0003).
 * Triển khai ma trận phân quyền vai trò × phạm vi × READ/WRITE, quy tắc lecturerIds độc lập,
 * định nghĩa strict empty scope và tính toán quyền tổng hợp theo phép HỢP (UNION).
 */
@Slf4j
@Service("scopeAuthorizationService")
@RequiredArgsConstructor
public class ScopeAuthorizationServiceImpl implements ScopeAuthorizationService {

    private final CourseSectionRepository courseSectionRepository;

    @Override
    public boolean canAccessSection(UserPrincipal principal, String sectionId, AccessType accessType) {
        // Nguyên tắc Fail-Closed: từ chối truy cập nếu thiếu tham số đầu vào cần thiết
        if (principal == null || sectionId == null || sectionId.isBlank() || accessType == null) {
            log.debug("canAccessSection: Fail-closed due to null or blank parameters (principal={}, sectionId={}, accessType={})",
                    principal != null ? principal.getUsername() : null, sectionId, accessType);
            return false;
        }

        if (principal.getRoleAssignments() == null || principal.getRoleAssignments().isEmpty()) {
            log.debug("canAccessSection: Fail-closed - user [{}] has no role assignments", principal.getUsername());
            return false;
        }

        // Truy vấn lớp học phần từ repository; nếu không tồn tại -> fail-closed trả về false
        Optional<CourseSection> sectionOpt = courseSectionRepository.findById(sectionId.trim());
        if (sectionOpt.isEmpty()) {
            log.debug("canAccessSection: Fail-closed - section [{}] not found in database", sectionId);
            return false;
        }

        CourseSection section = sectionOpt.get();

        // Quyền tổng hợp của người dùng có nhiều roleAssignments là phép HỢP (UNION):
        // Nếu BẤT KỲ roleAssignment nào cấp quyền truy cập, kết quả trả về true.
        for (RoleAssignment assignment : principal.getRoleAssignments()) {
            if (checkAssignment(assignment, principal, section, accessType)) {
                log.debug("canAccessSection: Access GRANTED to user [{}] for section [{}] with accessType [{}] via role [{}]",
                        principal.getUsername(), sectionId, accessType, assignment.getRole());
                return true;
            }
        }

        log.debug("canAccessSection: Access DENIED to user [{}] for section [{}] with accessType [{}]",
                principal.getUsername(), sectionId, accessType);
        return false;
    }

    /**
     * Kiểm tra quyền truy cập của một phân công vai trò cụ thể trên lớp học phần theo ma trận ADR 0003.
     */
    private boolean checkAssignment(RoleAssignment assignment, UserPrincipal principal, CourseSection section, AccessType accessType) {
        if (assignment == null || assignment.getRole() == null) {
            return false;
        }

        Role role = assignment.getRole();
        UserScope scope = assignment.getScope();
        String sectionId = section.getId();
        String courseId = section.getCourseId();
        String userId = principal.getId();

        if (accessType == AccessType.WRITE) {
            return switch (role) {
                // ADMIN: Toàn quyền ghi trên toàn bộ lớp học phần
                case ADMIN -> true;

                // LECTURER: Cho phép ghi khi user còn vai trò LECTURER VÀ:
                // (1) sectionId trong scope.sectionIds, HOẶC
                // (2) courseId trong scope.courseIds, HOẶC
                // (3) userId có trong CourseSection.lecturerIds (quy tắc độc lập từ phân công giảng dạy)
                case LECTURER -> matchesScope(scope, sectionId, courseId)
                        || isAssignedLecturer(section, userId);

                // Tất cả các vai trò khác không có quyền WRITE trên tài nguyên lớp học phần
                case PROGRAM_MANAGER, DEPARTMENT_HEAD, QA_OFFICER, VIEWER, STUDENT -> false;
            };
        } else if (accessType == AccessType.READ) {
            return switch (role) {
                // ADMIN: Toàn quyền đọc toàn bộ lớp học phần
                case ADMIN -> true;

                // LECTURER: Cho phép đọc khi thỏa mãn phạm vi scope hoặc nằm trong danh sách phân công giảng viên
                case LECTURER -> matchesScope(scope, sectionId, courseId)
                        || isAssignedLecturer(section, userId);

                // PROGRAM_MANAGER, DEPARTMENT_HEAD, QA_OFFICER:
                // Cho phép đọc khi lớp nằm trong scope.sectionIds hoặc courseId nằm trong scope.courseIds.
                // Nếu chỉ có programIds thì fail-closed (hạn chế kỹ thuật F01 chờ F02 ánh xạ CTĐT -> Lớp).
                case PROGRAM_MANAGER, DEPARTMENT_HEAD, QA_OFFICER -> matchesScope(scope, sectionId, courseId);

                // VIEWER:
                // - Strict empty scope (scope == null hoặc cả 3 mảng rỗng) -> Đọc toàn hệ thống.
                // - Non-empty scope -> Chỉ đọc trong phạm vi sectionIds hoặc courseIds.
                // - Nếu chỉ có programIds -> Scope không rỗng và không khớp section/course -> Fail-closed (từ chối 403).
                case VIEWER -> {
                    if (isStrictEmptyScope(scope)) {
                        yield true;
                    }
                    yield matchesScope(scope, sectionId, courseId);
                }

                // STUDENT: Từ chối truy cập trên tài nguyên lớp (chờ story NEW-03 xem kết quả cá nhân)
                case STUDENT -> false;
            };
        }

        return false;
    }

    /**
     * So khớp lớp học phần với phạm vi phân công (theo sectionIds hoặc courseIds).
     */
    private boolean matchesScope(UserScope scope, String sectionId, String courseId) {
        if (scope == null) {
            return false;
        }

        if (scope.getSectionIds() != null && sectionId != null && scope.getSectionIds().contains(sectionId)) {
            return true;
        }

        if (scope.getCourseIds() != null && courseId != null && scope.getCourseIds().contains(courseId)) {
            return true;
        }

        return false;
    }

    /**
     * Kiểm tra quy tắc độc lập: Giảng viên có tên trong danh sách phân công của lớp học phần hay không.
     */
    private boolean isAssignedLecturer(CourseSection section, String userId) {
        if (section.getLecturerIds() == null || userId == null) {
            return false;
        }
        return section.getLecturerIds().contains(userId);
    }

    /**
     * Định nghĩa Scope rỗng nghiêm ngặt (Strict Empty Scope per ADR 0003):
     * Scope chỉ rỗng khi scope == null HOẶC cả 3 mảng programIds, courseIds, sectionIds đều rỗng/null.
     */
    private boolean isStrictEmptyScope(UserScope scope) {
        if (scope == null) {
            return true;
        }
        return scope.isEmpty();
    }
}
