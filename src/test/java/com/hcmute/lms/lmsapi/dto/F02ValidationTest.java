package com.hcmute.lms.lmsapi.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("F02 DTO Validation Tests")
class F02ValidationTest {

  private static ValidatorFactory validatorFactory;
  private static Validator validator;

  @BeforeAll
  static void setUp() {
    validatorFactory = Validation.buildDefaultValidatorFactory();
    validator = validatorFactory.getValidator();
  }

  @AfterAll
  static void tearDown() {
    validatorFactory.close();
  }

  private PloRequest ploRequest(String code) {
    return PloRequest.builder().code(code).displayOrder(1).passThreshold(0.5).build();
  }

  private PiRequest piRequest(String code) {
    return PiRequest.builder()
        .ploId("671dcba5f123456789abc003")
        .code(code)
        .displayOrder(1)
        .weight(0.5)
        .passThreshold(0.5)
        .build();
  }

  @ParameterizedTest
  @ValueSource(strings = {"PLO1", "PLO2", "PLO10"})
  @DisplayName("Mã PLO hợp lệ")
  void ploCode_valid(String code) {
    assertThat(validator.validate(ploRequest(code))).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"PLO0", "PLO01", "plo1", "PLO", "PLO1A"})
  @DisplayName("Mã PLO sai định dạng bị từ chối")
  void ploCode_invalid(String code) {
    assertThat(validator.validate(ploRequest(code))).isNotEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"PI1.1", "PI6.2", "PI10.12"})
  @DisplayName("Mã PI hợp lệ")
  void piCode_valid(String code) {
    assertThat(validator.validate(piRequest(code))).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"PI0.1", "PI1.0", "PI01.1", "PI1", "PI1.1.1", "pi1.1"})
  @DisplayName("Mã PI sai định dạng bị từ chối")
  void piCode_invalid(String code) {
    assertThat(validator.validate(piRequest(code))).isNotEmpty();
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 3, 6})
  @DisplayName("Mức năng lực 1-6 hợp lệ")
  void capabilityLevel_valid(int level) {
    PloRequest request = ploRequest("PLO1");
    request.setCapabilityLevelRaw(level);
    assertThat(validator.validate(request)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 0, 7})
  @DisplayName("Mức năng lực ngoài 1-6 bị từ chối")
  void capabilityLevel_invalid(int level) {
    PloRequest request = ploRequest("PLO1");
    request.setCapabilityLevelRaw(level);
    assertThat(validator.validate(request)).isNotEmpty();
  }

  @Test
  @DisplayName("Tỷ lệ đạt mục tiêu null hoặc trong [0,1] hợp lệ")
  void targetPassRate_valid() {
    PiRequest request = piRequest("PI1.1");
    assertThat(validator.validate(request)).isEmpty();

    request.setTargetPassRate(0.0);
    assertThat(validator.validate(request)).isEmpty();

    request.setTargetPassRate(1.0);
    assertThat(validator.validate(request)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(doubles = {-0.1, 1.1})
  @DisplayName("Tỷ lệ đạt mục tiêu ngoài [0,1] bị từ chối")
  void targetPassRate_invalid(double rate) {
    PiRequest request = piRequest("PI1.1");
    request.setTargetPassRate(rate);
    assertThat(validator.validate(request)).isNotEmpty();
  }
}
