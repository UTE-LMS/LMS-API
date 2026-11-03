package com.hcmute.lms.lmsapi.service.impl;

import com.hcmute.lms.lmsapi.document.Course;
import com.hcmute.lms.lmsapi.document.ProgramCourse;
import com.hcmute.lms.lmsapi.dto.ProgramCourseRequest;
import com.hcmute.lms.lmsapi.dto.ProgramCourseResponse;
import com.hcmute.lms.lmsapi.dto.ProgramCourseUpdateRequest;
import com.hcmute.lms.lmsapi.exception.AppException;
import com.hcmute.lms.lmsapi.exception.BusinessRuleViolationException;
import com.hcmute.lms.lmsapi.exception.ErrorCode;
import com.hcmute.lms.lmsapi.exception.ResourceNotFoundException;
import com.hcmute.lms.lmsapi.mapper.ProgramCourseMapper;
import com.hcmute.lms.lmsapi.repository.CourseRepository;
import com.hcmute.lms.lmsapi.repository.ProgramCourseRepository;
import com.hcmute.lms.lmsapi.repository.ProgramVersionRepository;
import com.hcmute.lms.lmsapi.service.ProgramCourseService;
import com.hcmute.lms.lmsapi.util.ObjectIdUtils;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProgramCourseServiceImpl implements ProgramCourseService {

  private final ProgramCourseRepository programCourseRepository;
  private final ProgramVersionRepository programVersionRepository;
  private final CourseRepository courseRepository;
  private final ProgramCourseMapper programCourseMapper;

  @Override
  public List<ProgramCourseResponse> getCoursesByProgramVersion(String programVersionId) {
    requireProgramVersion(programVersionId);
    List<ProgramCourse> programCourses =
        programCourseRepository.findByProgramVersionIdOrderBySemesterAscCourseIdAsc(
            programVersionId);

    List<String> courseIds = programCourses.stream().map(ProgramCourse::getCourseId).toList();
    Map<String, Course> courseMap =
        courseRepository.findAllById(courseIds).stream()
            .collect(Collectors.toMap(Course::getId, Function.identity()));

    return programCourses.stream()
        .map(pc -> programCourseMapper.toResponse(pc, courseMap.get(pc.getCourseId())))
        .toList();
  }

  @Override
  public ProgramCourseResponse getProgramCourse(String programVersionId, String courseId) {
    requireProgramVersion(programVersionId);
    ObjectIdUtils.requireValid(courseId, "courseId");

    ProgramCourse programCourse =
        programCourseRepository
            .findByProgramVersionIdAndCourseId(programVersionId, courseId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "ProgramCourse", "courseId", courseId + " in PV " + programVersionId));

    Course course = courseRepository.findById(courseId).orElse(null);
    return programCourseMapper.toResponse(programCourse, course);
  }

  @Override
  public ProgramCourseResponse addCourseToProgramVersion(
      String programVersionId, ProgramCourseRequest request) {
    requireProgramVersion(programVersionId);
    ObjectIdUtils.requireValid(request.getCourseId(), "courseId");

    Course course =
        courseRepository
            .findById(request.getCourseId())
            .orElseThrow(
                () ->
                    new BusinessRuleViolationException(
                        "COURSE_NOT_FOUND", "Học phần không tồn tại: " + request.getCourseId()));

    if (programCourseRepository.existsByProgramVersionIdAndCourseId(
        programVersionId, request.getCourseId())) {
      throw conflict(request.getCourseId());
    }

    List<String> sanitizedPrereqs =
        validatePrerequisites(request.getCourseId(), request.getPrerequisiteCourseIds());

    Integer credits = request.getCredits() != null ? request.getCredits() : course.getCredits();

    ProgramCourse programCourse =
        ProgramCourse.builder()
            .programVersionId(programVersionId)
            .courseId(request.getCourseId())
            .semester(request.getSemester())
            .credits(credits)
            .type(request.getType())
            .category(request.getCategory())
            .prerequisiteCourseIds(sanitizedPrereqs)
            .build();

    try {
      programCourse = programCourseRepository.save(programCourse);
    } catch (DuplicateKeyException ex) {
      log.warn(
          "Duplicate program course mapping for pvId={} courseId={}",
          programVersionId,
          request.getCourseId());
      throw conflict(request.getCourseId());
    }

    log.info(
        "Course {} assigned to ProgramVersion {} successfully",
        request.getCourseId(),
        programVersionId);
    return programCourseMapper.toResponse(programCourse, course);
  }

  @Override
  public ProgramCourseResponse updateProgramCourse(
      String programVersionId, String courseId, ProgramCourseUpdateRequest request) {
    requireProgramVersion(programVersionId);
    ObjectIdUtils.requireValid(courseId, "courseId");

    ProgramCourse programCourse =
        programCourseRepository
            .findByProgramVersionIdAndCourseId(programVersionId, courseId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "ProgramCourse", "courseId", courseId + " in PV " + programVersionId));

    List<String> sanitizedPrereqs =
        validatePrerequisites(courseId, request.getPrerequisiteCourseIds());

    programCourse.setSemester(request.getSemester());
    programCourse.setCredits(request.getCredits());
    programCourse.setType(request.getType());
    programCourse.setCategory(request.getCategory());
    programCourse.setPrerequisiteCourseIds(sanitizedPrereqs);

    programCourse = programCourseRepository.save(programCourse);
    Course course = courseRepository.findById(courseId).orElse(null);

    log.info("ProgramCourse mapping updated for pvId={} courseId={}", programVersionId, courseId);
    return programCourseMapper.toResponse(programCourse, course);
  }

  @Override
  public void removeCourseFromProgramVersion(String programVersionId, String courseId) {
    requireProgramVersion(programVersionId);
    ObjectIdUtils.requireValid(courseId, "courseId");

    if (!programCourseRepository.existsByProgramVersionIdAndCourseId(programVersionId, courseId)) {
      throw new ResourceNotFoundException(
          "ProgramCourse", "courseId", courseId + " in PV " + programVersionId);
    }

    programCourseRepository.deleteByProgramVersionIdAndCourseId(programVersionId, courseId);
    log.info("Course {} removed from ProgramVersion {}", courseId, programVersionId);
  }

  private void requireProgramVersion(String programVersionId) {
    ObjectIdUtils.requireValid(programVersionId, "programVersionId");
    if (!programVersionRepository.existsById(programVersionId)) {
      throw new BusinessRuleViolationException(
          "PROGRAM_VERSION_NOT_FOUND",
          "Phiên bản chương trình đào tạo không tồn tại: " + programVersionId);
    }
  }

  private List<String> validatePrerequisites(String courseId, List<String> prerequisiteCourseIds) {
    if (prerequisiteCourseIds == null || prerequisiteCourseIds.isEmpty()) {
      return new ArrayList<>();
    }

    List<String> distinctPrereqs = new ArrayList<>(new LinkedHashSet<>(prerequisiteCourseIds));

    for (String prereqId : distinctPrereqs) {
      ObjectIdUtils.requireValid(prereqId, "prerequisiteCourseId");

      if (prereqId.equals(courseId)) {
        throw new BusinessRuleViolationException(
            "PREREQUISITE_SELF_REFERENCE",
            "Học phần không thể là học phần tiên quyết của chính nó: " + prereqId);
      }

      if (!courseRepository.existsById(prereqId)) {
        throw new BusinessRuleViolationException(
            "PREREQUISITE_NOT_FOUND", "Học phần tiên quyết không tồn tại: " + prereqId);
      }
    }

    return distinctPrereqs;
  }

  private AppException conflict(String courseId) {
    return new AppException(
        ErrorCode.CONFLICT,
        HttpStatus.CONFLICT,
        "Học phần đã được gán vào phiên bản chương trình đào tạo: " + courseId);
  }
}
