package com.hcmute.lms.lmsapi.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CloValidationTest (F03 / US-11, US-12)")
class CloValidationTest {

  private static Validator validator;

  @BeforeAll
  static void setUpValidator() {
    try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
      validator = factory.getValidator();
    }
  }

  @Test
  @DisplayName("US-11/AC-1: CloRequest hợp lệ với code ^CLO[0-9]+$ và bloomLevel 1-6")
  void validCloRequest_Success() {
    CloRequest request =
        CloRequest.builder()
            .code("CLO1")
            .bloomLevel(3)
            .contentOriginal("Nắm được cú pháp Java OOP")
            .build();

    Set<ConstraintViolation<CloRequest>> violations = validator.validate(request);
    assertThat(violations).isEmpty();
  }

  @Test
  @DisplayName("US-11/AC-1: Mã CLO sai định dạng (^CLO[0-9]+$) bị chặn")
  void invalidCloCode_FailsValidation() {
    CloRequest request =
        CloRequest.builder()
            .code("clo1") // chữ thường
            .bloomLevel(3)
            .build();

    Set<ConstraintViolation<CloRequest>> violations = validator.validate(request);
    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("code"));
  }

  @Test
  @DisplayName("US-11/AC-3: bloomLevel ngoài 1-6 (0 hoặc 7) bị chặn")
  void bloomLevelOutOfRange_FailsValidation() {
    CloRequest req0 = CloRequest.builder().code("CLO1").bloomLevel(0).build();
    Set<ConstraintViolation<CloRequest>> v0 = validator.validate(req0);
    assertThat(v0).anyMatch(v -> v.getPropertyPath().toString().equals("bloomLevel"));

    CloRequest req7 = CloRequest.builder().code("CLO1").bloomLevel(7).build();
    Set<ConstraintViolation<CloRequest>> v7 = validator.validate(req7);
    assertThat(v7).anyMatch(v -> v.getPropertyPath().toString().equals("bloomLevel"));
  }

  @Test
  @DisplayName("US-11/AC-4: measurement rỗng/null hợp lệ; ngoài [0, 1] bị chặn")
  void measurementValidation() {
    // null measurement
    CloRequest reqNull = CloRequest.builder().code("CLO1").bloomLevel(3).measurement(null).build();
    assertThat(validator.validate(reqNull)).isEmpty();

    // valid measurement
    CloRequest reqValid =
        CloRequest.builder()
            .code("CLO1")
            .bloomLevel(3)
            .measurement(
                CloMeasurementDto.builder().defaultWeight(0.4).defaultPassThreshold(0.6).build())
            .build();
    assertThat(validator.validate(reqValid)).isEmpty();

    // invalid measurement > 1.0
    CloRequest reqInvalid =
        CloRequest.builder()
            .code("CLO1")
            .bloomLevel(3)
            .measurement(
                CloMeasurementDto.builder().defaultWeight(1.5).defaultPassThreshold(-0.1).build())
            .build();
    Set<ConstraintViolation<CloRequest>> violations = validator.validate(reqInvalid);
    assertThat(violations).hasSize(2);
  }

  @Test
  @DisplayName("US-12/AC-5: weight ngoài (0, 1] bị chặn; null hoặc trong (0, 1] hợp lệ")
  void cloPiLinkWeightValidation() {
    String validId = new ObjectId().toHexString();

    // null weight hợp lệ (Q22)
    CloPiLinkRequest reqNullWeight =
        CloPiLinkRequest.builder()
            .programVersionId(validId)
            .cloId(validId)
            .piId(validId)
            .weight(null)
            .build();
    assertThat(validator.validate(reqNullWeight)).isEmpty();

    // weight = 0.5 hợp lệ
    CloPiLinkRequest reqValidWeight =
        CloPiLinkRequest.builder()
            .programVersionId(validId)
            .cloId(validId)
            .piId(validId)
            .weight(0.5)
            .build();
    assertThat(validator.validate(reqValidWeight)).isEmpty();

    // weight = 0.0 bị chặn (exclusiveMinimum: 0)
    CloPiLinkRequest reqZeroWeight =
        CloPiLinkRequest.builder()
            .programVersionId(validId)
            .cloId(validId)
            .piId(validId)
            .weight(0.0)
            .build();
    assertThat(validator.validate(reqZeroWeight))
        .anyMatch(v -> v.getPropertyPath().toString().equals("weight"));

    // weight = 1.05 bị chặn (> 1.0)
    CloPiLinkRequest reqOverWeight =
        CloPiLinkRequest.builder()
            .programVersionId(validId)
            .cloId(validId)
            .piId(validId)
            .weight(1.05)
            .build();
    assertThat(validator.validate(reqOverWeight))
        .anyMatch(v -> v.getPropertyPath().toString().equals("weight"));
  }
}
