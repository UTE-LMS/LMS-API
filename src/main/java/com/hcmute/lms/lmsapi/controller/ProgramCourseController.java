package com.hcmute.lms.lmsapi.controller;

import com.hcmute.lms.lmsapi.dto.ProgramCourseResponse;
import com.hcmute.lms.lmsapi.enums.CourseCategory;
import com.hcmute.lms.lmsapi.enums.CourseType;
import com.hcmute.lms.lmsapi.service.ProgramCourseService;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/program-versions/{programVersionId}/courses")
@RequiredArgsConstructor
public class ProgramCourseController {

  private final ProgramCourseService programCourseService;

  @GetMapping
  public ResponseEntity<List<ProgramCourseResponse>> getCourses(
      @PathVariable("programVersionId")
          @Pattern(
              regexp = "^[0-9a-fA-F]{24}$",
              message = "ID phiên bản phải là ObjectId 24 ký tự hex")
          String programVersionId,
      @RequestParam(required = false) Integer semester,
      @RequestParam(required = false) CourseType type,
      @RequestParam(required = false) CourseCategory category,
      @RequestParam(required = false) String search) {
    return ResponseEntity.ok(
        programCourseService.getCoursesByProgramVersion(
            programVersionId, semester, type, category, search));
  }

  @GetMapping("/{courseId}")
  public ResponseEntity<ProgramCourseResponse> getCourse(
      @PathVariable("programVersionId")
          @Pattern(
              regexp = "^[0-9a-fA-F]{24}$",
              message = "ID phiên bản phải là ObjectId 24 ký tự hex")
          String programVersionId,
      @PathVariable("courseId")
          @Pattern(
              regexp = "^[0-9a-fA-F]{24}$",
              message = "ID học phần phải là ObjectId 24 ký tự hex")
          String courseId) {
    return ResponseEntity.ok(programCourseService.getProgramCourse(programVersionId, courseId));
  }
}
