package com.hcmute.lms.lmsapi.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hcmute.lms.lmsapi.document.AuditLog;
import com.hcmute.lms.lmsapi.enums.AuditAction;
import com.hcmute.lms.lmsapi.repository.impl.AuditLogRepositoryCustomImpl;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditLogRepositoryCustom: Custom Query Unit Tests (OCP & DIP)")
public class AuditLogRepositoryCustomUnitTest {

  @Mock private MongoTemplate mongoTemplate;

  private AuditLogRepositoryCustom repositoryCustom;

  @BeforeEach
  void setUp() {
    repositoryCustom = new AuditLogRepositoryCustomImpl(mongoTemplate);
  }

  @Test
  @DisplayName("findLogsByCriteria constructs query with proper criteria and limit")
  void testFindLogsByCriteria() {
    Instant from = Instant.parse("2026-10-01T00:00:00Z");
    Instant to = Instant.parse("2026-10-27T23:59:59Z");

    when(mongoTemplate.find(org.mockito.ArgumentMatchers.any(Query.class), eq(AuditLog.class)))
        .thenReturn(List.of(new AuditLog()));

    List<AuditLog> result =
        repositoryCustom.findLogsByCriteria(
            "scores", "507f1f77bcf86cd799439011", AuditAction.SCORE_UPDATE, from, to, 10);

    assertThat(result).hasSize(1);

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
    verify(mongoTemplate).find(queryCaptor.capture(), eq(AuditLog.class));

    Query executedQuery = queryCaptor.getValue();
    assertThat(executedQuery.getLimit()).isEqualTo(10);
    assertThat(executedQuery.getSortObject().get("at")).isEqualTo(-1);

    String queryStr = executedQuery.getQueryObject().toString();
    assertThat(queryStr).contains("scores");
    assertThat(queryStr).contains("507f1f77bcf86cd799439011");
    assertThat(queryStr).contains("SCORE_UPDATE");
  }

  @Test
  @DisplayName("US-04: searchLogs lọc userId bằng ObjectId, sắp xếp at giảm dần và phân trang")
  void testSearchLogsBuildsPagedQuery() {
    String userId = "507f1f77bcf86cd799439011";
    Instant from = Instant.parse("2026-10-01T00:00:00Z");
    Instant to = Instant.parse("2026-10-27T23:59:59Z");

    // Trang đầy (10/10 dòng) nên tổng phải lấy từ count; trang ngắn thì Spring suy ra tổng từ
    // offset.
    when(mongoTemplate.count(any(Query.class), eq(AuditLog.class))).thenReturn(41L);
    when(mongoTemplate.find(any(Query.class), eq(AuditLog.class)))
        .thenReturn(java.util.Collections.nCopies(10, new AuditLog()));

    Page<AuditLog> page =
        repositoryCustom.searchLogs(
            userId, "users", "abc", AuditAction.LOGIN, from, to, PageRequest.of(2, 10));

    assertThat(page.getTotalElements()).isEqualTo(41L);
    assertThat(page.getNumber()).isEqualTo(2);

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
    verify(mongoTemplate).find(queryCaptor.capture(), eq(AuditLog.class));
    Query executed = queryCaptor.getValue();
    assertThat(executed.getSkip()).isEqualTo(20L);
    assertThat(executed.getLimit()).isEqualTo(10);
    assertThat(executed.getSortObject().get("at")).isEqualTo(-1);
    assertThat(executed.getSortObject().get("_id")).isEqualTo(-1);

    // userId phải là ObjectId BSON chứ không phải chuỗi, vì AuditLog.userId lưu dạng ObjectId.
    List<?> andClauses = (List<?>) executed.getQueryObject().get("$and");
    Object userIdCriterion =
        andClauses.stream()
            .map(clause -> ((Document) clause).get("userId"))
            .filter(Objects::nonNull)
            .findFirst()
            .orElseThrow();
    assertThat(userIdCriterion).isEqualTo(new ObjectId(userId));

    ArgumentCaptor<Query> countCaptor = ArgumentCaptor.forClass(Query.class);
    verify(mongoTemplate).count(countCaptor.capture(), eq(AuditLog.class));
    // Truy vấn đếm phải bỏ phân trang, nếu không tổng sẽ bị cắt theo skip/limit của trang.
    assertThat(countCaptor.getValue().isLimited()).isFalse();
    assertThat(countCaptor.getValue().getSkip()).isLessThanOrEqualTo(0L);
  }

  @Test
  @DisplayName("US-04: searchLogs không có tiêu chí thì truy vấn rỗng (lấy tất cả)")
  void testSearchLogsWithoutCriteria() {
    when(mongoTemplate.count(any(Query.class), eq(AuditLog.class))).thenReturn(0L);
    when(mongoTemplate.find(any(Query.class), eq(AuditLog.class))).thenReturn(List.of());

    repositoryCustom.searchLogs(null, "  ", null, null, null, null, PageRequest.of(0, 20));

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
    verify(mongoTemplate).find(queryCaptor.capture(), eq(AuditLog.class));
    assertThat(queryCaptor.getValue().getQueryObject()).isEmpty();
  }
}
