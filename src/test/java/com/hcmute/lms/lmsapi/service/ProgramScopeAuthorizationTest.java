package com.hcmute.lms.lmsapi.service;

import com.hcmute.lms.lmsapi.document.RoleAssignment;
import com.hcmute.lms.lmsapi.document.UserScope;
import com.hcmute.lms.lmsapi.dto.UserPrincipal;
import com.hcmute.lms.lmsapi.enums.AccessType;
import com.hcmute.lms.lmsapi.enums.Role;
import com.hcmute.lms.lmsapi.repository.CourseSectionRepository;
import com.hcmute.lms.lmsapi.service.impl.ScopeAuthorizationServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("canAccessProgram Unit Tests (F02 / ADR 0004)")
class ProgramScopeAuthorizationTest {

    private static final String PROGRAM_ID = "671dcba5f123456789abc001";
    private static final String OTHER_PROGRAM_ID = "671dcba5f123456789abc099";

    @Mock
    private CourseSectionRepository courseSectionRepository;

    @InjectMocks
    private ScopeAuthorizationServiceImpl service;

    private UserPrincipal principal(Role role, UserScope scope) {
        return UserPrincipal.builder()
                .id("671dcba5f123456789abc00a")
                .username("user")
                .roleAssignments(new ArrayList<>(List.of(
                        RoleAssignment.builder().role(role).scope(scope).build())))
                .build();
    }

    @Test
    @DisplayName("QA_OFFICER có programIds khớp được phép xác nhận")
    void qaOfficerMatchingScope_allowed() {
        UserScope scope = UserScope.builder().programIds(List.of(PROGRAM_ID)).build();

        assertThat(service.canAccessProgram(principal(Role.QA_OFFICER, scope), PROGRAM_ID, AccessType.WRITE))
                .isTrue();
    }

    @Test
    @DisplayName("QA_OFFICER không có programIds bị từ chối (fail-closed)")
    void qaOfficerEmptyScope_denied() {
        UserScope scope = UserScope.builder().build();

        assertThat(service.canAccessProgram(principal(Role.QA_OFFICER, scope), PROGRAM_ID, AccessType.WRITE))
                .isFalse();
    }

    @Test
    @DisplayName("QA_OFFICER khác chương trình bị từ chối")
    void qaOfficerOtherProgram_denied() {
        UserScope scope = UserScope.builder().programIds(List.of(OTHER_PROGRAM_ID)).build();

        assertThat(service.canAccessProgram(principal(Role.QA_OFFICER, scope), PROGRAM_ID, AccessType.WRITE))
                .isFalse();
    }

    @Test
    @DisplayName("PROGRAM_MANAGER có programIds khớp được phép")
    void programManagerMatchingScope_allowed() {
        UserScope scope = UserScope.builder().programIds(List.of(PROGRAM_ID)).build();

        assertThat(service.canAccessProgram(principal(Role.PROGRAM_MANAGER, scope), PROGRAM_ID, AccessType.WRITE))
                .isTrue();
    }

    @Test
    @DisplayName("ADMIN luôn được phép ở tầng service; controller vẫn chặn theo ADR 0004")
    void admin_allowed() {
        assertThat(service.canAccessProgram(principal(Role.ADMIN, null), PROGRAM_ID, AccessType.WRITE))
                .isTrue();
    }

    @Test
    @DisplayName("VIEWER chỉ đọc và cần scope rỗng nghiêm ngặt hoặc khớp chương trình")
    void viewer_readOnly() {
        assertThat(service.canAccessProgram(principal(Role.VIEWER, UserScope.builder().build()), PROGRAM_ID, AccessType.READ))
                .isTrue();
        assertThat(service.canAccessProgram(principal(Role.VIEWER, UserScope.builder().build()), PROGRAM_ID, AccessType.WRITE))
                .isFalse();
    }

    @Test
    @DisplayName("Tham số null hoặc rỗng bị từ chối")
    void nullParameters_denied() {
        assertThat(service.canAccessProgram(null, PROGRAM_ID, AccessType.WRITE)).isFalse();
        assertThat(service.canAccessProgram(principal(Role.QA_OFFICER, null), " ", AccessType.WRITE)).isFalse();
        assertThat(service.canAccessProgram(principal(Role.QA_OFFICER, null), PROGRAM_ID, null)).isFalse();
    }
}
