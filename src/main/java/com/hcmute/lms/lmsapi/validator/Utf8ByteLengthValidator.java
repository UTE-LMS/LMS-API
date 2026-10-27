package com.hcmute.lms.lmsapi.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

/**
 * Hiện thực kiểm tra ràng buộc độ dài byte UTF-8 cho annotation {@link Utf8ByteLength}.
 */
public class Utf8ByteLengthValidator implements ConstraintValidator<Utf8ByteLength, String> {

    private int min;
    private int max;

    @Override
    public void initialize(Utf8ByteLength constraintAnnotation) {
        this.min = constraintAnnotation.min();
        this.max = constraintAnnotation.max();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Cho phép giá trị null (để @NotBlank hoặc @NotNull xử lý riêng biệt)
        if (value == null) {
            return true;
        }

        int byteLength = value.getBytes(StandardCharsets.UTF_8).length;
        return byteLength >= min && byteLength <= max;
    }
}
