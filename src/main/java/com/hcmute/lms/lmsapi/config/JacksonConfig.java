package com.hcmute.lms.lmsapi.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.cfg.EnumFeature;

/**
 * Cấu hình Jackson dùng chung cho API.
 *
 * <p>Mặc định Jackson chấp nhận số (và chuỗi số) làm thứ tự (ordinal) của enum, nên {@code
 * "contribution": 1} trở thành {@code R}. Điều đó trái US-07/AC-2 (chỉ I/R/M) và tinh thần "I/R/M
 * không phải trọng số" (rule 02), đồng thời làm hợp đồng API phụ thuộc thứ tự khai báo enum. Bật
 * {@code FAIL_ON_NUMBERS_FOR_ENUMS} để mọi enum chỉ nhận đúng tên và số thì trả 400.
 */
@Configuration
public class JacksonConfig {

  @Bean
  JsonMapperBuilderCustomizer rejectNumericEnumsCustomizer() {
    return builder -> builder.enable(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS);
  }
}
