package com.hcmute.lms.lmsapi.document;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Empirical Challenge: UserScope Strict Empty Scope & Boundary Tests (ADR 0003 QĐ-5)")
public class UserScopeEmpiricalChallengeTest {

    @Nested
    @DisplayName("1. Null Scope Reference and Null Collections Handling")
    class NullHandlingTests {

        @Test
        @DisplayName("Direct method call on null UserScope reference throws NullPointerException")
        void testNullScopeReferenceThrowsNpe() {
            UserScope nullScope = null;
            // Calling instance method on null throws NPE
            assertThatThrownBy(() -> nullScope.isEmpty())
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("Safe evaluation idiom (scope == null || scope.isEmpty()) returns true for null scope")
        void testSafeEvaluationIdiomOnNullScope() {
            UserScope nullScope = null;
            boolean isScopeEmpty = (nullScope == null || nullScope.isEmpty());
            assertThat(isScopeEmpty).isTrue();
        }

        @Test
        @DisplayName("Scope object with all null lists ({ programIds: null, courseIds: null, sectionIds: null }) is empty")
        void testScopeWithAllNullListsIsEmpty() {
            UserScope scope = UserScope.builder()
                    .programIds(null)
                    .courseIds(null)
                    .sectionIds(null)
                    .build();
            assertThat(scope.isEmpty()).isTrue();
        }
    }

    @Nested
    @DisplayName("2. Empty Collections ({ programIds: [] })")
    class EmptyCollectionsTests {

        @Test
        @DisplayName("Scope with explicit empty programIds list ({ programIds: [] }) is empty")
        void testExplicitEmptyProgramIdsIsEmpty() {
            UserScope scope = UserScope.builder()
                    .programIds(Collections.emptyList())
                    .courseIds(Collections.emptyList())
                    .sectionIds(Collections.emptyList())
                    .build();
            assertThat(scope.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("Default no-arg constructor produces empty scope")
        void testDefaultConstructorProducesEmptyScope() {
            UserScope scope = new UserScope();
            assertThat(scope.isEmpty()).isTrue();
            assertThat(scope.getProgramIds()).isEmpty();
            assertThat(scope.getCourseIds()).isEmpty();
            assertThat(scope.getSectionIds()).isEmpty();
        }

        @Test
        @DisplayName("Lombok builder with no fields specified produces empty scope with empty lists")
        void testLombokBuilderDefaultsProduceEmptyScope() {
            UserScope scope = UserScope.builder().build();
            assertThat(scope.isEmpty()).isTrue();
            assertThat(scope.getProgramIds()).isNotNull().isEmpty();
            assertThat(scope.getCourseIds()).isNotNull().isEmpty();
            assertThat(scope.getSectionIds()).isNotNull().isEmpty();
        }
    }

    @Nested
    @DisplayName("3. Non-Empty Scopes ({ programIds: ['p1'] }, { sectionIds: ['s1'] })")
    class NonEmptyCollectionsTests {

        @Test
        @DisplayName("Scope with { programIds: ['p1'] } is NOT empty (Strict Empty Scope rule per ADR 0003 QĐ-5)")
        void testScopeWithSingleProgramIdIsNotEmpty() {
            UserScope scope = UserScope.builder()
                    .programIds(List.of("p1"))
                    .courseIds(Collections.emptyList())
                    .sectionIds(Collections.emptyList())
                    .build();

            // Strict Empty Scope: if programIds has elements, it is NOT empty!
            // This prevents VIEWER with only programIds from falling into system-wide READ branch.
            assertThat(scope.isEmpty()).isFalse();
        }

        @Test
        @DisplayName("Scope with { sectionIds: ['s1'] } is NOT empty")
        void testScopeWithSingleSectionIdIsNotEmpty() {
            UserScope scope = UserScope.builder()
                    .programIds(Collections.emptyList())
                    .courseIds(Collections.emptyList())
                    .sectionIds(List.of("s1"))
                    .build();
            assertThat(scope.isEmpty()).isFalse();
        }

        @Test
        @DisplayName("Scope with { courseIds: ['c1'] } is NOT empty")
        void testScopeWithSingleCourseIdIsNotEmpty() {
            UserScope scope = UserScope.builder()
                    .programIds(Collections.emptyList())
                    .courseIds(List.of("c1"))
                    .sectionIds(Collections.emptyList())
                    .build();
            assertThat(scope.isEmpty()).isFalse();
        }

        @Test
        @DisplayName("Scope with mixed populated lists is NOT empty")
        void testScopeWithMixedPopulatedListsIsNotEmpty() {
            UserScope scope = UserScope.builder()
                    .programIds(List.of("p1", "p2"))
                    .courseIds(List.of("c1"))
                    .sectionIds(List.of("s1", "s2", "s3"))
                    .build();
            assertThat(scope.isEmpty()).isFalse();
        }
    }

    @Nested
    @DisplayName("4. Edge Cases: Collections with Blank or Null Elements")
    class AdversarialEdgeCasesTests {

        @Test
        @DisplayName("Scope with programIds containing blank string { programIds: [''] } is NOT empty (size > 0)")
        void testProgramIdsWithEmptyStringIsNotEmpty() {
            UserScope scope = UserScope.builder()
                    .programIds(List.of(""))
                    .build();
            assertThat(scope.isEmpty()).isFalse();
        }

        @Test
        @DisplayName("Scope with sectionIds containing null element { sectionIds: [null] } is NOT empty (size > 0)")
        void testSectionIdsWithNullElementIsNotEmpty() {
            UserScope scope = UserScope.builder()
                    .sectionIds(Collections.singletonList(null))
                    .build();
            assertThat(scope.isEmpty()).isFalse();
        }
    }
}
