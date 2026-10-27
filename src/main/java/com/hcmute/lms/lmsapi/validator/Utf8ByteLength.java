package com.hcmute.lms.lmsapi.validator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Ràng buộc kiểm tra độ dài chuỗi theo số lượng byte UTF-8 (F01 / ADR 0003).
 * Dùng để bảo vệ chính sách mật khẩu BCrypt (tối đa 72 bytes) trước các ký tự tiếng Việt nhiều byte.
 */
@Documented
@Constraint(validatedBy = Utf8ByteLengthValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Utf8ByteLength {

    String message() default "Độ dài dữ liệu phải từ {min} đến {max} bytes UTF-8";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    int min() default 8;

    int max() default 72;
}
