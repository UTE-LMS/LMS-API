package com.hcmute.lms.lmsapi.service.impl;

import com.hcmute.lms.lmsapi.document.Course;
import com.hcmute.lms.lmsapi.dto.CourseRequest;
import com.hcmute.lms.lmsapi.dto.CourseResponse;
import com.hcmute.lms.lmsapi.dto.CourseUpdateRequest;
import com.hcmute.lms.lmsapi.dto.PageResponse;
import com.hcmute.lms.lmsapi.exception.AppException;
import com.hcmute.lms.lmsapi.exception.BusinessRuleViolationException;
import com.hcmute.lms.lmsapi.exception.ErrorCode;
import com.hcmute.lms.lmsapi.exception.ResourceNotFoundException;
import com.hcmute.lms.lmsapi.mapper.CourseMapper;
import com.hcmute.lms.lmsapi.repository.CoursePiContributionRepository;
import com.hcmute.lms.lmsapi.repository.CourseRepository;
import com.hcmute.lms.lmsapi.repository.ProgramCourseRepository;
import com.hcmute.lms.lmsapi.service.CourseService;
import com.hcmute.lms.lmsapi.util.ObjectIdUtils;
import com.hcmute.lms.lmsapi.util.PageableValidationUtils;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourseServiceImpl implements CourseService {

  private final CourseRepository courseRepository;
  private final ProgramCourseRepository programCourseRepository;
  private final CoursePiContributionRepository coursePiContributionRepository;
  private final CourseMapper courseMapper;

  @Override
  public PageResponse<CourseResponse> getCourses(int page, int size, String search) {
    PageableValidationUtils.validate(page, size);
    Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "code"));
    Page<Course> coursePage;

    if (search != null && !search.isBlank()) {
      String query = search.trim();
      coursePage =
          courseRepository.findByNameContainingIgnoreCaseOrCodeContainingIgnoreCase(
              query, query, pageable);
    } else {
      coursePage = courseRepository.findAll(pageable);
    }

    List<CourseResponse> content = courseMapper.toResponseList(coursePage.getContent());
    return PageResponse.of(
        content, coursePage.getNumber(), coursePage.getSize(), coursePage.getTotalElements());
  }

  @Override
  public CourseResponse getCourseById(String id) {
    ObjectIdUtils.requireValid(id, "id");
    Course course =
        courseRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Course", "id", id));
    return courseMapper.toResponse(course);
  }

  @Override
  public CourseResponse createCourse(CourseRequest request) {
    String code = request.getCode().trim();
    if (courseRepository.existsByCode(code)) {
      throw conflict(code);
    }

    Course course =
        Course.builder()
            .code(code)
            .name(request.getName().trim())
            .credits(request.getCredits())
            .extra(request.getExtra())
            .build();

    try {
      course = courseRepository.save(course);
    } catch (DuplicateKeyException ex) {
      log.warn("Duplicate course code on create: {}", code);
      throw conflict(code);
    }

    log.info("Course created successfully: id={}, code={}", course.getId(), course.getCode());
    return courseMapper.toResponse(course);
  }

  @Override
  public CourseResponse updateCourse(String id, CourseUpdateRequest request) {
    ObjectIdUtils.requireValid(id, "id");
    Course course =
        courseRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Course", "id", id));

    course.setName(request.getName().trim());
    course.setCredits(request.getCredits());
    course.setExtra(request.getExtra());

    course = courseRepository.save(course);
    log.info("Course updated successfully: id={}, code={}", course.getId(), course.getCode());
    return courseMapper.toResponse(course);
  }

  @Override
  public void deleteCourse(String id) {
    ObjectIdUtils.requireValid(id, "id");
    if (!courseRepository.existsById(id)) {
      throw new ResourceNotFoundException("Course", "id", id);
    }

    if (programCourseRepository.existsByCourseId(id)) {
      throw new BusinessRuleViolationException(
          "COURSE_IN_USE", "Không thể xóa học phần đang được gán vào chương trình đào tạo: " + id);
    }

    if (coursePiContributionRepository.existsByCourseId(id)) {
      throw new BusinessRuleViolationException(
          "COURSE_IN_USE",
          "Không thể xóa học phần đang được tham chiếu trong ma trận đóng góp PI: " + id);
    }

    courseRepository.deleteById(id);
    log.info("Course deleted successfully: id={}", id);
  }

  private AppException conflict(String code) {
    return new AppException(
        ErrorCode.CONFLICT, HttpStatus.CONFLICT, "Mã học phần đã tồn tại: " + code);
  }
}
