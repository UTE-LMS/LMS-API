package com.hcmute.lms.lmsapi.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/** Issue #17: khi không bật profile {@code demo}, seeder demo không tồn tại nên không thể chạy. */
@SpringBootTest
@DisplayName("Issue #17: DemoDataSeeder không chạy khi profile demo tắt")
class DemoDataSeederProfileOffTest {

  @Autowired private ApplicationContext applicationContext;

  @Test
  @DisplayName(
      "Issue #17: context mặc định không có bean DemoDataSeeder trong các ApplicationRunner")
  void seederBeanIsAbsentWithoutDemoProfile() {
    assertThat(applicationContext.getEnvironment().getActiveProfiles()).doesNotContain("demo");
    assertThat(applicationContext.getBeansOfType(DemoDataSeeder.class)).isEmpty();
    assertThat(applicationContext.getBeansOfType(ApplicationRunner.class).values())
        .noneMatch(runner -> runner instanceof DemoDataSeeder);
  }
}
