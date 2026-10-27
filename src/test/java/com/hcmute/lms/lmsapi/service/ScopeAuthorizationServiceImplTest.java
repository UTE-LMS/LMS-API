package com.hcmute.lms.lmsapi.service;

import com.hcmute.lms.lmsapi.document.CourseSection;
import com.hcmute.lms.lmsapi.document.Role;
import com.hcmute.lms.lmsapi.document.RoleAssignment;
import com.hcmute.lms.lmsapi.document.UserScope;
import com.hcmute.lms.lmsapi.dto.AccessType;
import com.hcmute.lms.lmsapi.dto.UserPrincipal;
import com.hcmute.lms.lmsapi.repository.CourseSectionRepository;
import com.hcmute.lms.lmsapi.service.impl.ScopeAuthorizationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit Test toàn diện ma trận phân quyền theo vai trò và phạm vi canAccessSection (F01 / US-03 / ADR 0003).
 * Kiểm thử chi tiết:
 * - Nguyên tắc Fail-Closed (tham số null, rỗng, không tìm thấy tài nguyên lớp)
 * - Toàn bộ ma trận vai trò × phạm vi × READ/WRITE
 * - Quy tắc lecturerIds độc lập cho vai trò LECTURER
 * - Định nghĩa Strict Empty Scope cho vai trò VIEWER (kể cả biên giới chỉ có programIds)
 * - Phép hợp (UNION) cho người dùng đa vai trò (Multi-Role)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("US-03: ScopeAuthorizationServiceImpl Unit & Matrix Tests (ADR 0003)")
class ScopeAuthorizationServiceImplTest {

    @Mock
    private CourseSectionRepository courseSectionRepository;

    @InjectMocks
    private ScopeAuthorizationServiceImpl scopeAuthorizationService;

    private static final String TARGET_SECTION_ID = "sec-001";
    private static final String OTHER_SECTION_ID = "sec-999";
    private static final String TARGET_COURSE_ID = "crs-100";
    private static final String OTHER_COURSE_ID = "crs-999";
    private static final String TARGET_LECTURER_ID = "user-lecturer-1";
    private static final String OTHER_USER_ID = "user-other-99";

    private CourseSection defaultCourseSection;

    @BeforeEach
    void setUp() {
        defaultCourseSection = CourseSection.builder()
                .id(TARGET_SECTION_ID)
                .code("SE401.P01")
                .courseId(TARGET_COURSE_ID)
                .syllabusId("syl-001")
                .termId("term-2026-1")
                .lecturerIds(new ArrayList<>(List.of(TARGET_LECTURER_ID)))
                .enrolledCount(45)
                .status("OPEN")
                .build();
    }

    private UserPrincipal createPrincipal(String userId, String username, List<RoleAssignment> assignments) {
        return UserPrincipal.builder()
                .id(userId)
                .username(username)
                .email(username + "@hcmute.edu.vn")
                .fullName("Test User " + username)
                .tokenVersion(0L)
                .roleAssignments(assignments != null ? new ArrayList<>(assignments) : null)
                .build();
    }

    // =========================================================================
    // 1. FAIL-CLOSED SECURITY TESTS
    // =========================================================================

    @Nested
    @DisplayName("1. Fail-Closed Security Tests")
    class FailClosedSecurityTests {

        @Test
        @DisplayName("principal == null -> trả về false")
        void principalNull_ReturnsFalse() {
            assertThat(scopeAuthorizationService.canAccessSection(null, TARGET_SECTION_ID, AccessType.READ)).isFalse();
            assertThat(scopeAuthorizationService.canAccessSection(null, TARGET_SECTION_ID, AccessType.WRITE)).isFalse();
        }

        @Test
        @DisplayName("sectionId null hoặc blank -> trả về false")
        void sectionIdNullOrBlank_ReturnsFalse() {
            UserPrincipal principal = createPrincipal(TARGET_LECTURER_ID, "lecturer", List.of(
                    RoleAssignment.builder().role(Role.ADMIN).build()
            ));
            assertThat(scopeAuthorizationService.canAccessSection(principal, null, AccessType.READ)).isFalse();
            assertThat(scopeAuthorizationService.canAccessSection(principal, "", AccessType.READ)).isFalse();
            assertThat(scopeAuthorizationService.canAccessSection(principal, "   ", AccessType.READ)).isFalse();
        }

        @Test
        @DisplayName("accessType == null -> trả về false")
        void accessTypeNull_ReturnsFalse() {
            UserPrincipal principal = createPrincipal(TARGET_LECTURER_ID, "admin", List.of(
                    RoleAssignment.builder().role(Role.ADMIN).build()
            ));
            assertThat(scopeAuthorizationService.canAccessSection(principal, TARGET_SECTION_ID, null)).isFalse();
        }

        @Test
        @DisplayName("Lớp học phần không tồn tại trong CSDL -> trả về false")
        void sectionNotFoundInDb_ReturnsFalse() {
            when(courseSectionRepository.findById("non-existent")).thenReturn(Optional.empty());

            UserPrincipal admin = createPrincipal("admin-id", "admin", List.of(
                    RoleAssignment.builder().role(Role.ADMIN).build()
            ));

            assertThat(scopeAuthorizationService.canAccessSection(admin, "non-existent", AccessType.READ)).isFalse();
            assertThat(scopeAuthorizationService.canAccessSection(admin, "non-existent", AccessType.WRITE)).isFalse();
        }

        @Test
        @DisplayName("Người dùng không có roleAssignments (null hoặc rỗng) -> trả về false")
        void roleAssignmentsNullOrEmpty_ReturnsFalse() {
            UserPrincipal noAssignments1 = createPrincipal("u1", "user1", null);
            UserPrincipal noAssignments2 = createPrincipal("u2", "user2", List.of());

            assertThat(scopeAuthorizationService.canAccessSection(noAssignments1, TARGET_SECTION_ID, AccessType.READ)).isFalse();
            assertThat(scopeAuthorizationService.canAccessSection(noAssignments2, TARGET_SECTION_ID, AccessType.READ)).isFalse();
        }

        @Test
        @DisplayName("roleAssignment chứa role == null -> bỏ qua an toàn và trả về false")
        void roleAssignmentWithNullRole_ReturnsFalse() {
            when(courseSectionRepository.findById(TARGET_SECTION_ID)).thenReturn(Optional.of(defaultCourseSection));

            UserPrincipal user = createPrincipal("u1", "user1", List.of(
                    RoleAssignment.builder().role(null).scope(null).build()
            ));

            assertThat(scopeAuthorizationService.canAccessSection(user, TARGET_SECTION_ID, AccessType.READ)).isFalse();
            assertThat(scopeAuthorizationService.canAccessSection(user, TARGET_SECTION_ID, AccessType.WRITE)).isFalse();
        }
    }

    // =========================================================================
    // 2. ADMIN ROLE TESTS
    // =========================================================================

    @Nested
    @DisplayName("2. ADMIN Role Tests (Full System Read & Write)")
    class AdminRoleTests {

        @BeforeEach
        void initSection() {
            when(courseSectionRepository.findById(TARGET_SECTION_ID)).thenReturn(Optional.of(defaultCourseSection));
        }

        @Test
        @DisplayName("ADMIN có quyền READ và WRITE toàn bộ lớp học phần kể cả khi scope null hoặc rỗng")
        void admin_FullAccess_RegardlessOfScope() {
            UserPrincipal adminNullScope = createPrincipal("admin-1", "admin1", List.of(
                    RoleAssignment.builder().role(Role.ADMIN).scope(null).build()
            ));
            UserPrincipal adminEmptyScope = createPrincipal("admin-2", "admin2", List.of(
                    RoleAssignment.builder().role(Role.ADMIN).scope(new UserScope()).build()
            ));

            assertThat(scopeAuthorizationService.canAccessSection(adminNullScope, TARGET_SECTION_ID, AccessType.READ)).isTrue();
            assertThat(scopeAuthorizationService.canAccessSection(adminNullScope, TARGET_SECTION_ID, AccessType.WRITE)).isTrue();

            assertThat(scopeAuthorizationService.canAccessSection(adminEmptyScope, TARGET_SECTION_ID, AccessType.READ)).isTrue();
            assertThat(scopeAuthorizationService.canAccessSection(adminEmptyScope, TARGET_SECTION_ID, AccessType.WRITE)).isTrue();
        }
    }

    // =========================================================================
    // 3. LECTURER ROLE & INDEPENDENT lecturerIds TESTS
    // =========================================================================

    @Nested
    @DisplayName("3. LECTURER Role & Independent lecturerIds Tests")
    class LecturerRoleTests {

        @BeforeEach
        void initSection() {
            when(courseSectionRepository.findById(TARGET_SECTION_ID)).thenReturn(Optional.of(defaultCourseSection));
        }

        @Test
        @DisplayName("LECTURER có sectionId trong scope.sectionIds -> READ và WRITE: CHO PHÉP")
        void lecturer_MatchingSectionId_Granted() {
            UserPrincipal lecturer = createPrincipal(OTHER_USER_ID, "lecturer_sec", List.of(
                    RoleAssignment.builder()
                            .role(Role.LECTURER)
                            .scope(UserScope.builder().sectionIds(List.of(TARGET_SECTION_ID)).build())
                            .build()
            ));

            assertThat(scopeAuthorizationService.canAccessSection(lecturer, TARGET_SECTION_ID, AccessType.READ)).isTrue();
            assertThat(scopeAuthorizationService.canAccessSection(lecturer, TARGET_SECTION_ID, AccessType.WRITE)).isTrue();
        }

        @Test
        @DisplayName("LECTURER có courseId trong scope.courseIds -> READ và WRITE: CHO PHÉP")
        void lecturer_MatchingCourseId_Granted() {
            UserPrincipal lecturer = createPrincipal(OTHER_USER_ID, "lecturer_crs", List.of(
                    RoleAssignment.builder()
                            .role(Role.LECTURER)
                            .scope(UserScope.builder().courseIds(List.of(TARGET_COURSE_ID)).build())
                            .build()
            ));

            assertThat(scopeAuthorizationService.canAccessSection(lecturer, TARGET_SECTION_ID, AccessType.READ)).isTrue();
            assertThat(scopeAuthorizationService.canAccessSection(lecturer, TARGET_SECTION_ID, AccessType.WRITE)).isTrue();
        }

        @Test
        @DisplayName("Quy tắc lecturerIds độc lập: LECTURER có scope rỗng hoặc null nhưng có tên trong lecturerIds -> READ và WRITE: CHO PHÉP")
        void lecturer_IndependentLecturerIdsRule_Granted() {
            // Giảng viên TARGET_LECTURER_ID có tên trong defaultCourseSection.lecturerIds
            UserPrincipal lecturerNullScope = createPrincipal(TARGET_LECTURER_ID, "lecturer_assigned_null", List.of(
                    RoleAssignment.builder().role(Role.LECTURER).scope(null).build()
            ));
            UserPrincipal lecturerEmptyScope = createPrincipal(TARGET_LECTURER_ID, "lecturer_assigned_empty", List.of(
                    RoleAssignment.builder().role(Role.LECTURER).scope(new UserScope()).build()
            ));
            // Phạm vi trỏ lớp khác nhưng nằm trong lecturerIds của lớp hiện tại
            UserPrincipal lecturerMismatchedScope = createPrincipal(TARGET_LECTURER_ID, "lecturer_assigned_mismatch", List.of(
                    RoleAssignment.builder()
                            .role(Role.LECTURER)
                            .scope(UserScope.builder().sectionIds(List.of(OTHER_SECTION_ID)).build())
                            .build()
            ));

            assertThat(scopeAuthorizationService.canAccessSection(lecturerNullScope, TARGET_SECTION_ID, AccessType.READ)).isTrue();
            assertThat(scopeAuthorizationService.canAccessSection(lecturerNullScope, TARGET_SECTION_ID, AccessType.WRITE)).isTrue();

            assertThat(scopeAuthorizationService.canAccessSection(lecturerEmptyScope, TARGET_SECTION_ID, AccessType.READ)).isTrue();
            assertThat(scopeAuthorizationService.canAccessSection(lecturerEmptyScope, TARGET_SECTION_ID, AccessType.WRITE)).isTrue();

            assertThat(scopeAuthorizationService.canAccessSection(lecturerMismatchedScope, TARGET_SECTION_ID, AccessType.READ)).isTrue();
            assertThat(scopeAuthorizationService.canAccessSection(lecturerMismatchedScope, TARGET_SECTION_ID, AccessType.WRITE)).isTrue();
        }

        @Test
        @DisplayName("LECTURER có scope rỗng và KHÔNG có tên trong lecturerIds -> READ và WRITE: TỪ CHỐI (403)")
        void lecturer_EmptyScope_NotAssigned_Denied() {
            UserPrincipal lecturer = createPrincipal(OTHER_USER_ID, "lecturer_unassigned", List.of(
                    RoleAssignment.builder().role(Role.LECTURER).scope(new UserScope()).build()
            ));

            assertThat(scopeAuthorizationService.canAccessSection(lecturer, TARGET_SECTION_ID, AccessType.READ)).isFalse();
            assertThat(scopeAuthorizationService.canAccessSection(lecturer, TARGET_SECTION_ID, AccessType.WRITE)).isFalse();
        }

        @Test
        @DisplayName("LECTURER chỉ có programIds và KHÔNG có tên trong lecturerIds -> READ và WRITE: TỪ CHỐI (403)")
        void lecturer_ProgramOnlyScope_NotAssigned_Denied() {
            UserPrincipal lecturer = createPrincipal(OTHER_USER_ID, "lecturer_prog_only", List.of(
                    RoleAssignment.builder()
                            .role(Role.LECTURER)
                            .scope(UserScope.builder().programIds(List.of("prog-001")).build())
                            .build()
            ));

            assertThat(scopeAuthorizationService.canAccessSection(lecturer, TARGET_SECTION_ID, AccessType.READ)).isFalse();
            assertThat(scopeAuthorizationService.canAccessSection(lecturer, TARGET_SECTION_ID, AccessType.WRITE)).isFalse();
        }
    }

    // =========================================================================
    // 4. MANAGEMENT ROLES (PROGRAM_MANAGER, DEPARTMENT_HEAD, QA_OFFICER)
    // =========================================================================

    @Nested
    @DisplayName("4. Management Roles Tests (PM, DH, QA)")
    class ManagementRolesTests {

        @BeforeEach
        void initSection() {
            when(courseSectionRepository.findById(TARGET_SECTION_ID)).thenReturn(Optional.of(defaultCourseSection));
        }

        @ParameterizedTest(name = "Role {0}: WRITE luôn bị từ chối 403 kể cả khi khớp scope")
        @EnumSource(value = Role.class, names = {"PROGRAM_MANAGER", "DEPARTMENT_HEAD", "QA_OFFICER"})
        void managementRoles_Write_AlwaysDenied(Role role) {
            UserPrincipal user = createPrincipal(OTHER_USER_ID, "manager_" + role.name(), List.of(
                    RoleAssignment.builder()
                            .role(role)
                            .scope(UserScope.builder()
                                    .sectionIds(List.of(TARGET_SECTION_ID))
                                    .courseIds(List.of(TARGET_COURSE_ID))
                                    .build())
                            .build()
            ));

            assertThat(scopeAuthorizationService.canAccessSection(user, TARGET_SECTION_ID, AccessType.WRITE)).isFalse();
        }

        @ParameterizedTest(name = "Role {0}: READ cho phép khi lớp nằm trong sectionIds hoặc courseIds")
        @EnumSource(value = Role.class, names = {"PROGRAM_MANAGER", "DEPARTMENT_HEAD", "QA_OFFICER"})
        void managementRoles_Read_ScopeMatches_Granted(Role role) {
            UserPrincipal userSec = createPrincipal(OTHER_USER_ID, "mgr_sec", List.of(
                    RoleAssignment.builder()
                            .role(role)
                            .scope(UserScope.builder().sectionIds(List.of(TARGET_SECTION_ID)).build())
                            .build()
            ));
            UserPrincipal userCrs = createPrincipal(OTHER_USER_ID, "mgr_crs", List.of(
                    RoleAssignment.builder()
                            .role(role)
                            .scope(UserScope.builder().courseIds(List.of(TARGET_COURSE_ID)).build())
                            .build()
            ));

            assertThat(scopeAuthorizationService.canAccessSection(userSec, TARGET_SECTION_ID, AccessType.READ)).isTrue();
            assertThat(scopeAuthorizationService.canAccessSection(userCrs, TARGET_SECTION_ID, AccessType.READ)).isTrue();
        }

        @ParameterizedTest(name = "Role {0}: READ từ chối khi chỉ có programIds (F01 limitation fail-closed)")
        @EnumSource(value = Role.class, names = {"PROGRAM_MANAGER", "DEPARTMENT_HEAD", "QA_OFFICER"})
        void managementRoles_Read_ProgramOnlyScope_Denied(Role role) {
            UserPrincipal user = createPrincipal(OTHER_USER_ID, "mgr_prog", List.of(
                    RoleAssignment.builder()
                            .role(role)
                            .scope(UserScope.builder().programIds(List.of("prog-101")).build())
                            .build()
            ));

            assertThat(scopeAuthorizationService.canAccessSection(user, TARGET_SECTION_ID, AccessType.READ)).isFalse();
        }

        @ParameterizedTest(name = "Role {0}: Không áp dụng quy tắc lecturerIds nếu không có vai trò LECTURER")
        @EnumSource(value = Role.class, names = {"PROGRAM_MANAGER", "DEPARTMENT_HEAD", "QA_OFFICER"})
        void managementRoles_InLecturerIds_WithoutLecturerRole_Denied(Role role) {
            // User TARGET_LECTURER_ID có tên trong defaultCourseSection.lecturerIds nhưng chỉ giữ vai trò quản lý
            UserPrincipal user = createPrincipal(TARGET_LECTURER_ID, "mgr_in_lecturer_list", List.of(
                    RoleAssignment.builder().role(role).scope(new UserScope()).build()
            ));

            assertThat(scopeAuthorizationService.canAccessSection(user, TARGET_SECTION_ID, AccessType.READ)).isFalse();
            assertThat(scopeAuthorizationService.canAccessSection(user, TARGET_SECTION_ID, AccessType.WRITE)).isFalse();
        }
    }

    // =========================================================================
    // 5. VIEWER ROLE TESTS (STRICT EMPTY SCOPE BOUNDARIES)
    // =========================================================================

    @Nested
    @DisplayName("5. VIEWER Role Tests (Strict Empty Scope & Fail-Closed Boundaries)")
    class ViewerRoleTests {

        @BeforeEach
        void initSection() {
            when(courseSectionRepository.findById(TARGET_SECTION_ID)).thenReturn(Optional.of(defaultCourseSection));
        }

        @Test
        @DisplayName("VIEWER: WRITE tuyệt đối bị từ chối 403 bất kể scope nào")
        void viewer_Write_AlwaysDenied() {
            UserPrincipal viewerNullScope = createPrincipal("v1", "viewer1", List.of(
                    RoleAssignment.builder().role(Role.VIEWER).scope(null).build()
            ));
            UserPrincipal viewerScoped = createPrincipal("v2", "viewer2", List.of(
                    RoleAssignment.builder()
                            .role(Role.VIEWER)
                            .scope(UserScope.builder().sectionIds(List.of(TARGET_SECTION_ID)).build())
                            .build()
            ));

            assertThat(scopeAuthorizationService.canAccessSection(viewerNullScope, TARGET_SECTION_ID, AccessType.WRITE)).isFalse();
            assertThat(scopeAuthorizationService.canAccessSection(viewerScoped, TARGET_SECTION_ID, AccessType.WRITE)).isFalse();
        }

        @Test
        @DisplayName("VIEWER: Strict Empty Scope (scope == null hoặc cả 3 mảng rỗng) -> READ toàn hệ thống: CHO PHÉP")
        void viewer_StrictEmptyScope_ReadSystemWide_Granted() {
            UserPrincipal viewerNullScope = createPrincipal("v1", "viewer1", List.of(
                    RoleAssignment.builder().role(Role.VIEWER).scope(null).build()
            ));
            UserPrincipal viewerEmptyScope = createPrincipal("v2", "viewer2", List.of(
                    RoleAssignment.builder().role(Role.VIEWER).scope(new UserScope()).build()
            ));
            UserPrincipal viewerEmptyLists = createPrincipal("v3", "viewer3", List.of(
                    RoleAssignment.builder()
                            .role(Role.VIEWER)
                            .scope(UserScope.builder()
                                    .programIds(new ArrayList<>())
                                    .courseIds(new ArrayList<>())
                                    .sectionIds(new ArrayList<>())
                                    .build())
                            .build()
            ));

            assertThat(scopeAuthorizationService.canAccessSection(viewerNullScope, TARGET_SECTION_ID, AccessType.READ)).isTrue();
            assertThat(scopeAuthorizationService.canAccessSection(viewerEmptyScope, TARGET_SECTION_ID, AccessType.READ)).isTrue();
            assertThat(scopeAuthorizationService.canAccessSection(viewerEmptyLists, TARGET_SECTION_ID, AccessType.READ)).isTrue();
        }

        @Test
        @DisplayName("VIEWER có scope: READ chỉ cho phép khi trùng sectionIds hoặc courseIds")
        void viewer_Scoped_ReadMatching_Granted() {
            UserPrincipal viewerSec = createPrincipal("v1", "viewer_sec", List.of(
                    RoleAssignment.builder()
                            .role(Role.VIEWER)
                            .scope(UserScope.builder().sectionIds(List.of(TARGET_SECTION_ID)).build())
                            .build()
            ));
            UserPrincipal viewerCrs = createPrincipal("v2", "viewer_crs", List.of(
                    RoleAssignment.builder()
                            .role(Role.VIEWER)
                            .scope(UserScope.builder().courseIds(List.of(TARGET_COURSE_ID)).build())
                            .build()
            ));

            assertThat(scopeAuthorizationService.canAccessSection(viewerSec, TARGET_SECTION_ID, AccessType.READ)).isTrue();
            assertThat(scopeAuthorizationService.canAccessSection(viewerCrs, TARGET_SECTION_ID, AccessType.READ)).isTrue();
        }

        @Test
        @DisplayName("ĐIỂM BIÊN QUAN TRỌNG: VIEWER chỉ có programIds thì KHÔNG PHẢI scope rỗng -> READ: TỪ CHỐI (403)")
        void viewer_ProgramOnlyScope_IsNotEmptyScope_Denied() {
            UserPrincipal viewerProgOnly = createPrincipal("v1", "viewer_prog_only", List.of(
                    RoleAssignment.builder()
                            .role(Role.VIEWER)
                            .scope(UserScope.builder().programIds(List.of("prog-1")).build())
                            .build()
            ));

            assertThat(scopeAuthorizationService.canAccessSection(viewerProgOnly, TARGET_SECTION_ID, AccessType.READ)).isFalse();
        }

        @Test
        @DisplayName("VIEWER có scope lệch (không chứa sectionId hay courseId tương ứng) -> READ: TỪ CHỐI (403)")
        void viewer_MismatchedScope_Denied() {
            UserPrincipal viewerMismatch = createPrincipal("v1", "viewer_mismatch", List.of(
                    RoleAssignment.builder()
                            .role(Role.VIEWER)
                            .scope(UserScope.builder()
                                    .sectionIds(List.of(OTHER_SECTION_ID))
                                    .courseIds(List.of(OTHER_COURSE_ID))
                                    .build())
                            .build()
            ));

            assertThat(scopeAuthorizationService.canAccessSection(viewerMismatch, TARGET_SECTION_ID, AccessType.READ)).isFalse();
        }
    }

    // =========================================================================
    // 6. STUDENT ROLE TESTS
    // =========================================================================

    @Nested
    @DisplayName("6. STUDENT Role Tests (Always 403 on Sections)")
    class StudentRoleTests {

        @BeforeEach
        void initSection() {
            when(courseSectionRepository.findById(TARGET_SECTION_ID)).thenReturn(Optional.of(defaultCourseSection));
        }

        @Test
        @DisplayName("STUDENT bị từ chối 403 trên cả READ và WRITE (chờ story NEW-03)")
        void student_ReadAndWrite_AlwaysDenied() {
            UserPrincipal student = createPrincipal("st-1", "student1", List.of(
                    RoleAssignment.builder().role(Role.STUDENT).scope(null).build()
            ));

            assertThat(scopeAuthorizationService.canAccessSection(student, TARGET_SECTION_ID, AccessType.READ)).isFalse();
            assertThat(scopeAuthorizationService.canAccessSection(student, TARGET_SECTION_ID, AccessType.WRITE)).isFalse();
        }
    }

    // =========================================================================
    // 7. MULTI-ROLE UNION TESTS
    // =========================================================================

    @Nested
    @DisplayName("7. Multi-Role UNION Tests")
    class MultiRoleUnionTests {

        @BeforeEach
        void initSection() {
            when(courseSectionRepository.findById(TARGET_SECTION_ID)).thenReturn(Optional.of(defaultCourseSection));
        }

        @Test
        @DisplayName("Người dùng có STUDENT + LECTURER (được phân công) -> Kế thừa quyền LECTURER (WRITE & READ CHO PHÉP)")
        void multiRole_StudentPlusLecturer_GrantsLecturerAccess() {
            UserPrincipal multiUser = createPrincipal(TARGET_LECTURER_ID, "ta_student_lecturer", List.of(
                    RoleAssignment.builder().role(Role.STUDENT).scope(null).build(),
                    RoleAssignment.builder().role(Role.LECTURER).scope(null).build()
            ));

            assertThat(scopeAuthorizationService.canAccessSection(multiUser, TARGET_SECTION_ID, AccessType.READ)).isTrue();
            assertThat(scopeAuthorizationService.canAccessSection(multiUser, TARGET_SECTION_ID, AccessType.WRITE)).isTrue();
        }

        @Test
        @DisplayName("Người dùng có PROGRAM_MANAGER (quản lý lớp) + VIEWER (scope rỗng) -> READ: CHO PHÉP (qua VIEWER), WRITE: TỪ CHỐI (cả 2 đều ko có WRITE)")
        void multiRole_ProgramManagerPlusViewer_UnionReadSuccess_WriteDenied() {
            UserPrincipal multiUser = createPrincipal(OTHER_USER_ID, "pm_viewer", List.of(
                    RoleAssignment.builder().role(Role.PROGRAM_MANAGER).scope(UserScope.builder().programIds(List.of("prog-1")).build()).build(),
                    RoleAssignment.builder().role(Role.VIEWER).scope(null).build()
            ));

            // READ: PM bị 403 (chỉ có programIds), nhưng VIEWER (scope rỗng) cho phép -> UNION: true
            assertThat(scopeAuthorizationService.canAccessSection(multiUser, TARGET_SECTION_ID, AccessType.READ)).isTrue();
            // WRITE: PM không có WRITE, VIEWER không có WRITE -> UNION: false
            assertThat(scopeAuthorizationService.canAccessSection(multiUser, TARGET_SECTION_ID, AccessType.WRITE)).isFalse();
        }

        @Test
        @DisplayName("Người dùng có QA_OFFICER (courseIds) + LECTURER (sectionIds khác) -> Phép hợp trên 2 tài nguyên khác nhau")
        void multiRole_QaPlusLecturer_DisjointScopes() {
            CourseSection otherSectionOfTargetCourse = CourseSection.builder()
                    .id(OTHER_SECTION_ID)
                    .courseId(TARGET_COURSE_ID)
                    .lecturerIds(List.of("another-lecturer"))
                    .build();

            when(courseSectionRepository.findById(OTHER_SECTION_ID)).thenReturn(Optional.of(otherSectionOfTargetCourse));

            // User là QA phụ trách TARGET_COURSE_ID, đồng thời là LECTURER chỉ phụ trách TARGET_SECTION_ID
            UserPrincipal multiUser = createPrincipal(OTHER_USER_ID, "qa_and_lecturer", List.of(
                    RoleAssignment.builder()
                            .role(Role.QA_OFFICER)
                            .scope(UserScope.builder().courseIds(List.of(TARGET_COURSE_ID)).build())
                            .build(),
                    RoleAssignment.builder()
                            .role(Role.LECTURER)
                            .scope(UserScope.builder().sectionIds(List.of(TARGET_SECTION_ID)).build())
                            .build()
            ));

            // Trên TARGET_SECTION_ID: LECTURER cấp WRITE và READ; QA cấp READ -> Cả READ & WRITE đều CHO PHÉP
            assertThat(scopeAuthorizationService.canAccessSection(multiUser, TARGET_SECTION_ID, AccessType.READ)).isTrue();
            assertThat(scopeAuthorizationService.canAccessSection(multiUser, TARGET_SECTION_ID, AccessType.WRITE)).isTrue();

            // Trên OTHER_SECTION_ID (cùng courseId): QA cấp READ; LECTURER không cấp quyền -> READ: CHO PHÉP, WRITE: TỪ CHỐI
            assertThat(scopeAuthorizationService.canAccessSection(multiUser, OTHER_SECTION_ID, AccessType.READ)).isTrue();
            assertThat(scopeAuthorizationService.canAccessSection(multiUser, OTHER_SECTION_ID, AccessType.WRITE)).isFalse();
        }
    }
}
