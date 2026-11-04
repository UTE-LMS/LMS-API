package com.hcmute.lms.lmsapi.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.hcmute.lms.lmsapi.enums.TermStatus;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TermValidationTest (F05a / US-18)")
class TermValidationTest {

  private static Validator validator;

  @BeforeAll
  static void setUpValidator() {
    try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
      validator = factory.getValidator();
    }
  }

  @Test
  @DisplayName("US-18: TermRequest hợp lệ")
  void validTermRequest_Success() {
    TermRequest req = TermRequest.builder().code("2026-HK1").academicYear("2025-2026").build();

    Set<ConstraintViolation<TermRequest>> violations = validator.validate(req);
    assertThat(violations).isEmpty();
  }

  @Test
  @DisplayName("US-18: TermRequest để trống code bị chặn")
  void blankCode_FailsValidation() {
    TermRequest req = TermRequest.builder().code("   ").build();
    Set<ConstraintViolation<TermRequest>> violations = validator.validate(req);
    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("code"));
  }

  @Test
  @DisplayName("US-18: TermRequest mã code có ký tự đặc biệt bị chặn")
  void invalidCodePattern_FailsValidation() {
    TermRequest req = TermRequest.builder().code("2026 HK1!@#").build();
    Set<ConstraintViolation<TermRequest>> violations = validator.validate(req);
    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("code"));
  }

  @Test
  @DisplayName("US-18: TermStatusUpdateRequest để trống status bị chặn")
  void nullStatus_FailsValidation() {
    TermStatusUpdateRequest req = TermStatusUpdateRequest.builder().status(null).build();
    Set<ConstraintViolation<TermStatusUpdateRequest>> violations = validator.validate(req);
    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("status"));
  }

  @Test
  @DisplayName("US-18: TermStatusUpdateRequest hợp lệ")
  void validStatusUpdate_Success() {
    TermStatusUpdateRequest req =
        TermStatusUpdateRequest.builder().status(TermStatus.SCORE_LOCKED).build();
    Set<ConstraintViolation<TermStatusUpdateRequest>> violations = validator.validate(req);
    assertThat(violations).isEmpty();
  }
}
