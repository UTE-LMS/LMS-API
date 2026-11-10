package com.hcmute.lms.lmsapi.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hcmute.lms.lmsapi.document.Enrollment;
import com.hcmute.lms.lmsapi.document.Student;
import com.hcmute.lms.lmsapi.enums.EnrollmentStatus;
import com.hcmute.lms.lmsapi.testsupport.ScoreImportIntegrationBase;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

/**
 * Phi chức năng (plan F05b §8, NFR đề xuất N1/NFR-03, CHƯA được GVHD chốt): tệp 5.000 dòng x 2 câu
 * = 10.000 điểm, đo cả ba bước tải, kiểm tra, xác nhận trên MongoDB replica set cục bộ. Ngưỡng 10
 * giây là con số đề xuất trong plan, không phải cam kết đã ký.
 */
@DisplayName("F05b (phi chức năng đề xuất N1): nhập 5.000 dòng trong ≤ 10 giây")
class ImportBatchPerformanceIntegrationTest extends ScoreImportIntegrationBase {

  private static final int ROWS = 5000;
  private static final Duration LIMIT = Duration.ofSeconds(10);

  @Test
  @DisplayName(
      "US-24 & N1 (đề xuất): 5.000 dòng x 2 câu -> 10.000 điểm, tải + kiểm tra + xác nhận ≤ 10 giây")
  void fiveThousandRowsWithinTenSeconds() throws Exception {
    List<Student> students = new ArrayList<>(ROWS);
    for (int i = 0; i < ROWS; i++) {
      students.add(Student.builder().studentRef(fixtures.code("STU")).build());
    }
    List<Student> saved = new ArrayList<>(mongo().insert(students, Student.class));
    List<Enrollment> enrollments = new ArrayList<>(ROWS);
    for (Student student : saved) {
      enrollments.add(
          Enrollment.builder()
              .sectionId(section.getId())
              .studentId(student.getId())
              .status(EnrollmentStatus.ENROLLED)
              .enrolledAt(Instant.now())
              .build());
    }
    mongo().insert(enrollments, Enrollment.class);
    StringBuilder rows = new StringBuilder("studentRef,Q1,Q2\n");
    for (Student student : saved) {
      rows.append(student.getStudentRef()).append(",3,5\n");
    }

    Instant start = Instant.now();
    String id = uploadOk(rows.toString(), "MISSING");
    long afterUpload = Duration.between(start, Instant.now()).toMillis();
    mockMvc.perform(postAction(lecturerToken, id, "validate")).andExpect(status().isOk());
    long afterValidate = Duration.between(start, Instant.now()).toMillis();
    mockMvc
        .perform(postAction(lecturerToken, id, "confirm"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stats.scoreCount").value(ROWS * 2));
    Duration elapsed = Duration.between(start, Instant.now());

    assertThat(mongo().count(Query.query(Criteria.where("importBatchId").exists(true)), "scores"))
        .isGreaterThanOrEqualTo(ROWS * 2L);
    assertThat(elapsed)
        .as("thời gian tải + kiểm tra + xác nhận %s", elapsed)
        .isLessThanOrEqualTo(LIMIT);
    System.out.println(
        "F05b-PERF 5000 rows x 2 items (10000 scores) upload ms="
            + afterUpload
            + " validate-at ms="
            + afterValidate
            + " total ms="
            + elapsed.toMillis());
  }
}
