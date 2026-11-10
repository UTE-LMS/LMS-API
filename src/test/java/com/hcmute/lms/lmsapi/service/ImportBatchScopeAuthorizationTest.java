package com.hcmute.lms.lmsapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.hcmute.lms.lmsapi.document.CourseSection;
import com.hcmute.lms.lmsapi.document.ImportBatch;
import com.hcmute.lms.lmsapi.document.RoleAssignment;
import com.hcmute.lms.lmsapi.document.UserScope;
import com.hcmute.lms.lmsapi.dto.UserPrincipal;
import com.hcmute.lms.lmsapi.enums.AccessType;
import com.hcmute.lms.lmsapi.enums.Role;
import com.hcmute.lms.lmsapi.repository.CourseSectionRepository;
import com.hcmute.lms.lmsapi.repository.ImportBatchRepository;
import com.hcmute.lms.lmsapi.service.impl.ScopeAuthorizationServiceImpl;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("F05b: canAccessImportBatch - quyền trên lô nhập suy từ quyền trên lớp (fail-closed)")
class ImportBatchScopeAuthorizationTest {

  @Mock private CourseSectionRepository courseSectionRepository;
  @Mock private ImportBatchRepository importBatchRepository;
  @InjectMocks private ScopeAuthorizationServiceImpl service;

  private static UserPrincipal principal(String id, Role role) {
    return UserPrincipal.builder()
        .id(id)
        .username("u-" + id)
        .roleAssignments(
            List.of(RoleAssignment.builder().role(role).scope(UserScope.builder().build()).build()))
        .build();
  }

  @Test
  @DisplayName(
      "Chung: giảng viên phụ trách lớp được đọc/ghi lô của lớp; giảng viên khác bị từ chối")
  void accessFollowsTheSectionOfTheBatch() {
    when(importBatchRepository.findById("b1"))
        .thenReturn(Optional.of(ImportBatch.builder().id("b1").sectionId("s1").build()));
    when(courseSectionRepository.findById("s1"))
        .thenReturn(
            Optional.of(
                CourseSection.builder()
                    .id("s1")
                    .courseId("c1")
                    .lecturerIds(List.of("lec"))
                    .build()));

    assertThat(
            service.canAccessImportBatch(principal("lec", Role.LECTURER), "b1", AccessType.WRITE))
        .isTrue();
    assertThat(
            service.canAccessImportBatch(principal("lec", Role.LECTURER), " b1 ", AccessType.READ))
        .isTrue();
    assertThat(
            service.canAccessImportBatch(principal("khac", Role.LECTURER), "b1", AccessType.READ))
        .isFalse();
    assertThat(
            service.canAccessImportBatch(principal("khac", Role.LECTURER), "b1", AccessType.WRITE))
        .isFalse();
  }

  @Test
  @DisplayName("Chung: lô không tồn tại, id rỗng/null, người dùng null -> từ chối (fail-closed)")
  void failClosed() {
    when(importBatchRepository.findById("missing")).thenReturn(Optional.empty());

    assertThat(
            service.canAccessImportBatch(
                principal("lec", Role.LECTURER), "missing", AccessType.READ))
        .isFalse();
    assertThat(service.canAccessImportBatch(principal("lec", Role.LECTURER), " ", AccessType.READ))
        .isFalse();
    assertThat(service.canAccessImportBatch(principal("lec", Role.LECTURER), null, AccessType.READ))
        .isFalse();
    when(importBatchRepository.findById("b1"))
        .thenReturn(Optional.of(ImportBatch.builder().id("b1").sectionId("s1").build()));
    assertThat(service.canAccessImportBatch(null, "b1", AccessType.READ)).isFalse();
  }
}
