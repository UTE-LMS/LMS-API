package com.hcmute.lms.lmsapi.util;

import com.hcmute.lms.lmsapi.dto.FieldErrorDetail;
import com.hcmute.lms.lmsapi.exception.PageableValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PageableValidationUtils Unit Tests (F01 / US-02)")
class PageableValidationUtilsTest {

    @Nested
    @DisplayName("Valid Pageable Parameter Tests")
    class ValidTests {

        @Test
        @DisplayName("Should pass validation with default valid page and size (0, 20)")
        void shouldPassWithDefaultValidValues() {
            PageableValidationUtils.validate(0, 20);
        }

        @Test
        @DisplayName("Should pass validation with boundaries (page=0, size=1 and size=100)")
        void shouldPassWithBoundaryValues() {
            PageableValidationUtils.validate(0, 1);
            PageableValidationUtils.validate(0, 100);
            PageableValidationUtils.validate(1000, 50);
        }

        @Test
        @DisplayName("createPageable should apply defaults (0, 20) when inputs are null")
        void shouldApplyDefaultsWhenInputsAreNull() {
            Pageable pageable = PageableValidationUtils.createPageable(null, null);

            assertThat(pageable.getPageNumber()).isZero();
            assertThat(pageable.getPageSize()).isEqualTo(20);
        }

        @Test
        @DisplayName("createPageable should retain valid explicit inputs")
        void shouldRetainValidExplicitInputs() {
            Pageable pageable = PageableValidationUtils.createPageable(3, 40);

            assertThat(pageable.getPageNumber()).isEqualTo(3);
            assertThat(pageable.getPageSize()).isEqualTo(40);
        }
    }

    @Nested
    @DisplayName("Invalid Pageable Parameter Tests (HTTP 400 VALIDATION_ERROR)")
    class InvalidTests {

        @Test
        @DisplayName("Should reject negative page index (page < 0)")
        void shouldRejectNegativePageIndex() {
            assertThatThrownBy(() -> PageableValidationUtils.validate(-1, 20))
                    .isInstanceOf(PageableValidationException.class)
                    .satisfies(ex -> {
                        PageableValidationException pve = (PageableValidationException) ex;
                        List<FieldErrorDetail> errors = pve.getFieldErrors();
                        assertThat(errors).hasSize(1);
                        assertThat(errors.get(0).getField()).isEqualTo("page");
                        assertThat(errors.get(0).getRejectedValue()).isEqualTo(-1);
                    });
        }

        @Test
        @DisplayName("Should reject zero page size (size = 0)")
        void shouldRejectZeroPageSize() {
            assertThatThrownBy(() -> PageableValidationUtils.validate(0, 0))
                    .isInstanceOf(PageableValidationException.class)
                    .satisfies(ex -> {
                        PageableValidationException pve = (PageableValidationException) ex;
                        List<FieldErrorDetail> errors = pve.getFieldErrors();
                        assertThat(errors).hasSize(1);
                        assertThat(errors.get(0).getField()).isEqualTo("size");
                        assertThat(errors.get(0).getRejectedValue()).isEqualTo(0);
                    });
        }

        @Test
        @DisplayName("Should reject negative page size (size < 0)")
        void shouldRejectNegativePageSize() {
            assertThatThrownBy(() -> PageableValidationUtils.validate(0, -10))
                    .isInstanceOf(PageableValidationException.class)
                    .satisfies(ex -> {
                        PageableValidationException pve = (PageableValidationException) ex;
                        List<FieldErrorDetail> errors = pve.getFieldErrors();
                        assertThat(errors).hasSize(1);
                        assertThat(errors.get(0).getField()).isEqualTo("size");
                        assertThat(errors.get(0).getRejectedValue()).isEqualTo(-10);
                    });
        }

        @Test
        @DisplayName("Should reject page size exceeding 100 (size > 100)")
        void shouldRejectPageSizeExceeding100() {
            assertThatThrownBy(() -> PageableValidationUtils.validate(0, 101))
                    .isInstanceOf(PageableValidationException.class)
                    .satisfies(ex -> {
                        PageableValidationException pve = (PageableValidationException) ex;
                        List<FieldErrorDetail> errors = pve.getFieldErrors();
                        assertThat(errors).hasSize(1);
                        assertThat(errors.get(0).getField()).isEqualTo("size");
                        assertThat(errors.get(0).getRejectedValue()).isEqualTo(101);
                    });
        }

        @Test
        @DisplayName("Should report both page and size errors when both are invalid")
        void shouldReportBothPageAndSizeErrors() {
            assertThatThrownBy(() -> PageableValidationUtils.validate(-2, 105))
                    .isInstanceOf(PageableValidationException.class)
                    .satisfies(ex -> {
                        PageableValidationException pve = (PageableValidationException) ex;
                        List<FieldErrorDetail> errors = pve.getFieldErrors();
                        assertThat(errors).hasSize(2);
                        assertThat(errors).extracting(FieldErrorDetail::getField).containsExactlyInAnyOrder("page", "size");
                    });
        }

        @Test
        @DisplayName("createPageable should throw PageableValidationException on invalid inputs")
        void createPageableShouldThrowOnInvalidInputs() {
            assertThatThrownBy(() -> PageableValidationUtils.createPageable(-1, 50))
                    .isInstanceOf(PageableValidationException.class);

            assertThatThrownBy(() -> PageableValidationUtils.createPageable(0, 200))
                    .isInstanceOf(PageableValidationException.class);
        }
    }
}
