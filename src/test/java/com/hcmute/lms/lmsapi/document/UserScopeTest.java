package com.hcmute.lms.lmsapi.document;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UserScope: Strict Empty Scope Unit Tests (ADR 0003 QĐ-5)")
public class UserScopeTest {

    @Test
    @DisplayName("Scope with default empty lists is empty")
    void testDefaultScope_IsEmpty() {
        UserScope scope = new UserScope();
        assertThat(scope.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("Scope with null lists is empty")
    void testScopeWithNullLists_IsEmpty() {
        UserScope scope = UserScope.builder()
                .programIds(null)
                .courseIds(null)
                .sectionIds(null)
                .build();
        assertThat(scope.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("Scope with explicit empty lists is empty")
    void testScopeWithEmptyLists_IsEmpty() {
        UserScope scope = UserScope.builder()
                .programIds(Collections.emptyList())
                .courseIds(Collections.emptyList())
                .sectionIds(Collections.emptyList())
                .build();
        assertThat(scope.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("Scope with only programIds is NOT empty (Strict Empty Scope rule)")
    void testScopeWithOnlyProgramIds_IsNotEmpty() {
        UserScope scope = UserScope.builder()
                .programIds(List.of("507f1f77bcf86cd799439011"))
                .courseIds(Collections.emptyList())
                .sectionIds(Collections.emptyList())
                .build();
        assertThat(scope.isEmpty()).isFalse();
    }

    @Test
    @DisplayName("Scope with only courseIds is NOT empty")
    void testScopeWithOnlyCourseIds_IsNotEmpty() {
        UserScope scope = UserScope.builder()
                .programIds(Collections.emptyList())
                .courseIds(List.of("507f1f77bcf86cd799439012"))
                .sectionIds(Collections.emptyList())
                .build();
        assertThat(scope.isEmpty()).isFalse();
    }

    @Test
    @DisplayName("Scope with only sectionIds is NOT empty")
    void testScopeWithOnlySectionIds_IsNotEmpty() {
        UserScope scope = UserScope.builder()
                .programIds(Collections.emptyList())
                .courseIds(Collections.emptyList())
                .sectionIds(List.of("507f1f77bcf86cd799439013"))
                .build();
        assertThat(scope.isEmpty()).isFalse();
    }
}
