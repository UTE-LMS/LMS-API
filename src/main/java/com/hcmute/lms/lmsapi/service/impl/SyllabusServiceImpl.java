package com.hcmute.lms.lmsapi.service.impl;

import com.hcmute.lms.lmsapi.document.Syllabus;
import com.hcmute.lms.lmsapi.dto.SyllabusRequest;
import com.hcmute.lms.lmsapi.dto.SyllabusResponse;
import com.hcmute.lms.lmsapi.enums.SyllabusStatus;
import com.hcmute.lms.lmsapi.exception.AppException;
import com.hcmute.lms.lmsapi.exception.BusinessRuleViolationException;
import com.hcmute.lms.lmsapi.exception.ErrorCode;
import com.hcmute.lms.lmsapi.exception.ResourceNotFoundException;
import com.hcmute.lms.lmsapi.mapper.SyllabusMapper;
import com.hcmute.lms.lmsapi.repository.CourseRepository;
import com.hcmute.lms.lmsapi.repository.SyllabusRepository;
import com.hcmute.lms.lmsapi.service.SyllabusService;
import com.hcmute.lms.lmsapi.util.ObjectIdUtils;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SyllabusServiceImpl implements SyllabusService {

  private final SyllabusRepository syllabusRepository;
  private final CourseRepository courseRepository;
  private final SyllabusMapper syllabusMapper;

  @Override
  public List<SyllabusResponse> getSyllabi(String courseId) {
    requireCourse(courseId);
    return syllabusMapper.toResponseList(
        syllabusRepository.findByCourseIdOrderByVersionAsc(courseId));
  }

  @Override
  public SyllabusResponse getSyllabus(String id) {
    return syllabusMapper.toResponse(requireSyllabus(id));
  }

  @Override
  public SyllabusResponse createSyllabus(String courseId, SyllabusRequest request) {
    requireCourse(courseId);

    String version = request.getVersion().trim();
    if (syllabusRepository.existsByCourseIdAndVersion(courseId, version)) {
      throw conflict(
          String.format(
              "Đề cương phiên bản [%s] đã tồn tại cho học phần id=[%s]", version, courseId));
    }

    // US-10/AC-2: Nếu cả hai học kỳ được cung cấp, kiểm tra sơ bộ (ràng buộc niên khóa chi tiết chờ
    // F05a)
    if (request.getEffectiveFromTermId() != null && request.getEffectiveToTermId() != null) {
      ObjectIdUtils.requireValid(request.getEffectiveFromTermId(), "effectiveFromTermId");
      ObjectIdUtils.requireValid(request.getEffectiveToTermId(), "effectiveToTermId");
    }

    Syllabus syllabus = syllabusMapper.toDocument(courseId, request);
    try {
      Syllabus saved = syllabusRepository.save(syllabus);
      log.info(
          "Tạo thành công đề cương id=[{}] phiên bản [{}] cho học phần id=[{}]",
          saved.getId(),
          saved.getVersion(),
          courseId);
      return syllabusMapper.toResponse(saved);
    } catch (DuplicateKeyException ex) {
      throw conflict(
          String.format(
              "Đề cương phiên bản [%s] đã tồn tại cho học phần id=[%s]", version, courseId));
    }
  }

  @Override
  public SyllabusResponse updateStatus(String id, SyllabusStatus newStatus) {
    Syllabus syllabus = requireSyllabus(id);
    SyllabusStatus currentStatus = syllabus.getStatus();

    if (currentStatus == newStatus) {
      return syllabusMapper.toResponse(syllabus);
    }

    boolean validTransition =
        (currentStatus == SyllabusStatus.DRAFT && newStatus == SyllabusStatus.CONFIRMED)
            || (currentStatus == SyllabusStatus.CONFIRMED && newStatus == SyllabusStatus.ARCHIVED);

    if (!validTransition) {
      throw new BusinessRuleViolationException(
          String.format(
              "Không thể chuyển trạng thái đề cương từ [%s] sang [%s]", currentStatus, newStatus));
    }

    syllabus.setStatus(newStatus);
    Syllabus updated = syllabusRepository.save(syllabus);
    log.info(
        "Cập nhật trạng thái đề cương id=[{}] từ [{}] sang [{}]", id, currentStatus, newStatus);
    return syllabusMapper.toResponse(updated);
  }

  private void requireCourse(String courseId) {
    ObjectIdUtils.requireValid(courseId, "courseId");
    if (!courseRepository.existsById(courseId)) {
      throw new ResourceNotFoundException("Học phần không tồn tại: " + courseId);
    }
  }

  private Syllabus requireSyllabus(String id) {
    ObjectIdUtils.requireValid(id, "syllabusId");
    return syllabusRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Đề cương không tồn tại: " + id));
  }

  private AppException conflict(String message) {
    return new AppException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, message);
  }
}
