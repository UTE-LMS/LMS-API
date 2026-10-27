package com.hcmute.lms.lmsapi.service.impl;

import com.hcmute.lms.lmsapi.document.RoleAssignment;
import com.hcmute.lms.lmsapi.document.UserScope;
import com.hcmute.lms.lmsapi.exception.BusinessRuleViolationException;
import com.hcmute.lms.lmsapi.repository.ScopeValidationRepository;
import com.hcmute.lms.lmsapi.service.ScopeValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Hiện thực dịch vụ kiểm tra tính hợp lệ của phạm vi phụ trách (Scope Validation Service).
 * Tuân thủ ADR 0002: Gọi repository interface {@link ScopeValidationRepository}, tuyệt đối không gọi MongoTemplate.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScopeValidationServiceImpl implements ScopeValidationService {

    private final ScopeValidationRepository scopeValidationRepository;

    @Override
    public void validateScope(UserScope scope) {
        if (scope == null || scope.isEmpty()) {
            return;
        }

        // 1. Kiểm tra tồn tại của các programIds trong collection programs
        if (scope.getProgramIds() != null) {
            for (String programId : scope.getProgramIds()) {
                if (programId != null && !programId.isBlank()) {
                    if (!scopeValidationRepository.existsProgram(programId.trim())) {
                        log.warn("Scope validation failed: Program [{}] does not exist", programId);
                        throw new BusinessRuleViolationException(
                                "SCOPE_NOT_FOUND",
                                "Chương trình đào tạo không tồn tại trong hệ thống: " + programId
                        );
                    }
                }
            }
        }

        // 2. Kiểm tra tồn tại của các courseIds trong collection courses
        if (scope.getCourseIds() != null) {
            for (String courseId : scope.getCourseIds()) {
                if (courseId != null && !courseId.isBlank()) {
                    if (!scopeValidationRepository.existsCourse(courseId.trim())) {
                        log.warn("Scope validation failed: Course [{}] does not exist", courseId);
                        throw new BusinessRuleViolationException(
                                "SCOPE_NOT_FOUND",
                                "Học phần không tồn tại trong hệ thống: " + courseId
                        );
                    }
                }
            }
        }

        // 3. Kiểm tra tồn tại của các sectionIds trong collection course_sections
        if (scope.getSectionIds() != null) {
            for (String sectionId : scope.getSectionIds()) {
                if (sectionId != null && !sectionId.isBlank()) {
                    if (!scopeValidationRepository.existsSection(sectionId.trim())) {
                        log.warn("Scope validation failed: CourseSection [{}] does not exist", sectionId);
                        throw new BusinessRuleViolationException(
                                "SCOPE_NOT_FOUND",
                                "Lớp học phần không tồn tại trong hệ thống: " + sectionId
                        );
                    }
                }
            }
        }
    }

    @Override
    public void validateRoleAssignments(List<RoleAssignment> roleAssignments) {
        if (roleAssignments == null || roleAssignments.isEmpty()) {
            return;
        }

        for (RoleAssignment roleAssignment : roleAssignments) {
            if (roleAssignment != null && roleAssignment.getScope() != null) {
                validateScope(roleAssignment.getScope());
            }
        }
    }
}
