package com.hcmute.lms.lmsapi.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hcmute.lms.lmsapi.document.AuditEntity;
import com.hcmute.lms.lmsapi.document.AuditLog;
import com.hcmute.lms.lmsapi.document.RoleAssignment;
import com.hcmute.lms.lmsapi.document.User;
import com.hcmute.lms.lmsapi.enums.AuditAction;
import com.hcmute.lms.lmsapi.enums.Role;
import com.hcmute.lms.lmsapi.enums.UserStatus;
import com.hcmute.lms.lmsapi.repository.AuditLogRepository;
import com.hcmute.lms.lmsapi.repository.UserRepository;
import com.hcmute.lms.lmsapi.service.AuditService;
import com.hcmute.lms.lmsapi.testsupport.IntegrationTestBase;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * US-39/AC-2, nhánh lỗi do chính CSDL: bước sau của transaction vi phạm chỉ mục duy nhất (không
 * phải ngoại lệ do test ném) thì bản ghi nghiệp vụ ghi ở bước trước và bản ghi audit cùng bị
 * rollback. Bổ sung cho {@link TransactionRollbackIntegrationTest} (ngoại lệ ném tay).
 */
@Import(TransactionRollbackOnDuplicateKeyIntegrationTest.DuplicateKeyOperation.class)
@DisplayName("US-39/AC-2: rollback khi bước sau vi phạm khóa duy nhất")
class TransactionRollbackOnDuplicateKeyIntegrationTest extends IntegrationTestBase {

  @Autowired private DuplicateKeyOperation operation;
  @Autowired private UserRepository userRepository;
  @Autowired private AuditLogRepository auditLogRepository;

  @Test
  @DisplayName(
      "US-39/AC-2: DuplicateKeyException giữa transaction -> user, audit ghi trước đó đều không còn")
  void duplicateKeyRollsBackEarlierWritesAndAudit() {
    String username = fixtures.username("tx");
    String firstId = new ObjectId().toHexString();

    assertThatThrownBy(() -> operation.createTwiceWithSameUsername(username, firstId))
        .isInstanceOf(DuplicateKeyException.class);

    assertThat(userRepository.findByUsername(username))
        .as("user ghi ở bước 1 phải bị rollback")
        .isEmpty();
    List<AuditLog> audits =
        auditLogRepository.findByEntityCollectionAndEntityIdOrderByAtDesc("users", firstId);
    assertThat(audits).as("audit ghi ở bước 2 phải bị rollback").isEmpty();
  }

  @Test
  @DisplayName(
      "US-39/AC-2 (đối chứng): cùng thao tác nhưng username khác nhau -> cả hai user và audit được commit")
  void withoutDuplicateEverythingCommits() {
    String firstId = new ObjectId().toHexString();

    operation.createTwo(fixtures.username("tx"), fixtures.username("tx"), firstId);

    assertThat(auditLogRepository.findByEntityCollectionAndEntityIdOrderByAtDesc("users", firstId))
        .hasSize(1);
    auditLogRepository.deleteAll(
        auditLogRepository.findByEntityCollectionAndEntityIdOrderByAtDesc("users", firstId));
  }

  /** Thao tác nghiệp vụ giả có @Transactional: ghi user, ghi audit, rồi ghi user thứ hai. */
  @TestComponent
  @RequiredArgsConstructor
  public static class DuplicateKeyOperation {

    private final MongoTemplate mongoTemplate;
    private final AuditService auditService;

    @Transactional
    public void createTwiceWithSameUsername(String username, String firstId) {
      createTwo(username, username, firstId);
    }

    @Transactional
    public void createTwo(String firstUsername, String secondUsername, String firstId) {
      mongoTemplate.insert(user(firstId, firstUsername));
      auditService.record(
          AuditAction.LOGIN,
          AuditEntity.builder().collection("users").id(firstId).build(),
          null,
          null,
          null);
      mongoTemplate.insert(user(new ObjectId().toHexString(), secondUsername));
    }

    private static User user(String id, String username) {
      Instant now = Instant.now();
      return User.builder()
          .id(id)
          .username(username)
          .email(username + "@itest.invalid")
          .passwordHash("not-a-real-hash")
          .fullName("[DỮ LIỆU THỬ NGHIỆM] " + username)
          .status(UserStatus.ACTIVE)
          .roleAssignments(List.of(RoleAssignment.builder().role(Role.QA).build()))
          .createdAt(now)
          .updatedAt(now)
          .build();
    }
  }
}
