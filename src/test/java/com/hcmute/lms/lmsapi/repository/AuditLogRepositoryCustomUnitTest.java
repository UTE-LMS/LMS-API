package com.hcmute.lms.lmsapi.repository;

import com.hcmute.lms.lmsapi.document.AuditAction;
import com.hcmute.lms.lmsapi.document.AuditLog;
import com.hcmute.lms.lmsapi.repository.impl.AuditLogRepositoryCustomImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditLogRepositoryCustom: Custom Query Unit Tests (OCP & DIP)")
public class AuditLogRepositoryCustomUnitTest {

    @Mock
    private MongoTemplate mongoTemplate;

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

        List<AuditLog> result = repositoryCustom.findLogsByCriteria(
                "scores",
                "507f1f77bcf86cd799439011",
                AuditAction.SCORE_UPDATE,
                from,
                to,
                10
        );

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
}
