package com.hcmute.lms.lmsapi.service.impl;

import com.hcmute.lms.lmsapi.enums.Role;
import com.hcmute.lms.lmsapi.document.RoleAssignment;
import com.hcmute.lms.lmsapi.document.UserScope;
import com.hcmute.lms.lmsapi.exception.BusinessRuleViolationException;
import com.hcmute.lms.lmsapi.repository.ScopeValidationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScopeValidationServiceImpl Unit Tests (F01 / US-02)")
class ScopeValidationServiceImplTest {

    @Mock
    private ScopeValidationRepository scopeValidationRepository;

    @InjectMocks
    private ScopeValidationServiceImpl scopeValidationService;

    private static final String VALID_PROGRAM_ID = "671dcba5f123456789abcd01";
    private static final String VALID_COURSE_ID = "671dcba5f123456789abcd02";
    private static final String VALID_SECTION_ID = "671dcba5f123456789abcd03";

    private static final String INVALID_PROGRAM_ID = "671dcba5f123456789abce01";
    private static final String INVALID_COURSE_ID = "671dcba5f123456789abce02";
    private static final String INVALID_SECTION_ID = "671dcba5f123456789abce03";

    @Nested
    @DisplayName("Empty or Null Scope Tests")
    class EmptyScopeTests {

        @Test
        @DisplayName("validateScope should succeed without repository lookup when scope is null")
        void shouldSucceedWhenScopeIsNull() {
            assertThatNoException().isThrownBy(() -> scopeValidationService.validateScope(null));
            verify(scopeValidationRepository, never()).existsProgram(anyString());
        }

        @Test
        @DisplayName("validateScope should succeed without repository lookup when scope is empty")
        void shouldSucceedWhenScopeIsEmpty() {
            UserScope emptyScope = UserScope.builder()
                    .programIds(List.of())
                    .courseIds(List.of())
                    .sectionIds(List.of())
                    .build();

            assertThatNoException().isThrownBy(() -> scopeValidationService.validateScope(emptyScope));
            verify(scopeValidationRepository, never()).existsProgram(anyString());
        }

        @Test
        @DisplayName("validateRoleAssignments should succeed when roleAssignments list is null or empty")
        void shouldSucceedWhenRoleAssignmentsEmpty() {
            assertThatNoException().isThrownBy(() -> scopeValidationService.validateRoleAssignments(null));
            assertThatNoException().isThrownBy(() -> scopeValidationService.validateRoleAssignments(List.of()));
        }
    }

    @Nested
    @DisplayName("Valid Scope ID Tests")
    class ValidScopeTests {

        @Test
        @DisplayName("validateScope should succeed when all program, course, and section IDs exist in DB")
        void shouldSucceedWhenAllIdsExist() {
            when(scopeValidationRepository.existsProgram(VALID_PROGRAM_ID)).thenReturn(true);
            when(scopeValidationRepository.existsCourse(VALID_COURSE_ID)).thenReturn(true);
            when(scopeValidationRepository.existsSection(VALID_SECTION_ID)).thenReturn(true);

            UserScope scope = UserScope.builder()
                    .programIds(List.of(VALID_PROGRAM_ID))
                    .courseIds(List.of(VALID_COURSE_ID))
                    .sectionIds(List.of(VALID_SECTION_ID))
                    .build();

            assertThatNoException().isThrownBy(() -> scopeValidationService.validateScope(scope));

            verify(scopeValidationRepository).existsProgram(VALID_PROGRAM_ID);
            verify(scopeValidationRepository).existsCourse(VALID_COURSE_ID);
            verify(scopeValidationRepository).existsSection(VALID_SECTION_ID);
        }

        @Test
        @DisplayName("validateRoleAssignments should validate scopes across all assignments")
        void shouldValidateAllRoleAssignments() {
            when(scopeValidationRepository.existsCourse(VALID_COURSE_ID)).thenReturn(true);
            when(scopeValidationRepository.existsSection(VALID_SECTION_ID)).thenReturn(true);

            List<RoleAssignment> assignments = List.of(
                    RoleAssignment.builder().role(Role.ADMIN).scope(null).build(),
                    RoleAssignment.builder().role(Role.LECTURER).scope(
                            UserScope.builder()
                                    .courseIds(List.of(VALID_COURSE_ID))
                                    .sectionIds(List.of(VALID_SECTION_ID))
                                    .build()
                    ).build()
            );

            assertThatNoException().isThrownBy(() -> scopeValidationService.validateRoleAssignments(assignments));
        }
    }

    @Nested
    @DisplayName("Invalid Scope ID Tests (HTTP 422 BUSINESS_RULE_VIOLATION)")
    class InvalidScopeTests {

        @Test
        @DisplayName("Should throw BusinessRuleViolationException when program ID does not exist")
        void shouldThrowWhenProgramIdDoesNotExist() {
            when(scopeValidationRepository.existsProgram(INVALID_PROGRAM_ID)).thenReturn(false);

            UserScope scope = UserScope.builder()
                    .programIds(List.of(INVALID_PROGRAM_ID))
                    .build();

            assertThatThrownBy(() -> scopeValidationService.validateScope(scope))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("Chương trình đào tạo không tồn tại trong hệ thống")
                    .hasMessageContaining(INVALID_PROGRAM_ID);
        }

        @Test
        @DisplayName("Should throw BusinessRuleViolationException when course ID does not exist")
        void shouldThrowWhenCourseIdDoesNotExist() {
            when(scopeValidationRepository.existsCourse(INVALID_COURSE_ID)).thenReturn(false);

            UserScope scope = UserScope.builder()
                    .courseIds(List.of(INVALID_COURSE_ID))
                    .build();

            assertThatThrownBy(() -> scopeValidationService.validateScope(scope))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("Học phần không tồn tại trong hệ thống")
                    .hasMessageContaining(INVALID_COURSE_ID);
        }

        @Test
        @DisplayName("Should throw BusinessRuleViolationException when section ID does not exist")
        void shouldThrowWhenSectionIdDoesNotExist() {
            when(scopeValidationRepository.existsSection(INVALID_SECTION_ID)).thenReturn(false);

            UserScope scope = UserScope.builder()
                    .sectionIds(List.of(INVALID_SECTION_ID))
                    .build();

            assertThatThrownBy(() -> scopeValidationService.validateScope(scope))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("Lớp học phần không tồn tại trong hệ thống")
                    .hasMessageContaining(INVALID_SECTION_ID);
        }
    }
}
