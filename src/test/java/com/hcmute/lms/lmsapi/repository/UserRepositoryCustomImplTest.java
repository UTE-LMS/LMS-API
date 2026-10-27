package com.hcmute.lms.lmsapi.repository;

import com.hcmute.lms.lmsapi.document.User;
import com.hcmute.lms.lmsapi.repository.impl.UserRepositoryCustomImpl;
import com.mongodb.client.result.UpdateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserRepositoryCustomImpl: Atomic TokenVersion Increment Unit Tests (ADR 0002 & ADR 0003)")
public class UserRepositoryCustomImplTest {

    @Mock
    private MongoTemplate mongoTemplate;

    private UserRepositoryCustom userRepositoryCustom;

    @BeforeEach
    void setUp() {
        userRepositoryCustom = new UserRepositoryCustomImpl(mongoTemplate);
    }

    @Test
    @DisplayName("incrementTokenVersion returns 0 when userId is null or empty without calling MongoTemplate")
    void testIncrementTokenVersion_NullOrBlank() {
        assertThat(userRepositoryCustom.incrementTokenVersion(null)).isEqualTo(0L);
        assertThat(userRepositoryCustom.incrementTokenVersion("")).isEqualTo(0L);
        assertThat(userRepositoryCustom.incrementTokenVersion("   ")).isEqualTo(0L);

        verify(mongoTemplate, never()).updateFirst(any(), any(), any(Class.class));
    }

    @Test
    @DisplayName("incrementTokenVersion executes $inc on tokenVersion for valid ObjectId string")
    void testIncrementTokenVersion_ValidObjectId() {
        String validObjectId = "507f1f77bcf86cd799439011";
        UpdateResult updateResult = mock(UpdateResult.class);
        when(updateResult.getModifiedCount()).thenReturn(1L);
        when(updateResult.getMatchedCount()).thenReturn(1L);

        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(User.class)))
                .thenReturn(updateResult);

        long modifiedCount = userRepositoryCustom.incrementTokenVersion(validObjectId);

        assertThat(modifiedCount).isEqualTo(1L);

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);

        verify(mongoTemplate).updateFirst(queryCaptor.capture(), updateCaptor.capture(), eq(User.class));

        Query executedQuery = queryCaptor.getValue();
        Update executedUpdate = updateCaptor.getValue();

        assertThat(executedQuery.getQueryObject().toString()).contains(validObjectId);
        assertThat(executedUpdate.getUpdateObject().toString()).contains("$inc");
        assertThat(executedUpdate.getUpdateObject().toString()).contains("tokenVersion");
    }
}
