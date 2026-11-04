package com.hcmute.lms.lmsapi.service.impl;

import com.hcmute.lms.lmsapi.document.Term;
import com.hcmute.lms.lmsapi.dto.TermRequest;
import com.hcmute.lms.lmsapi.dto.TermResponse;
import com.hcmute.lms.lmsapi.dto.TermStatusUpdateRequest;
import com.hcmute.lms.lmsapi.dto.TermUpdateRequest;
import com.hcmute.lms.lmsapi.enums.TermStatus;
import com.hcmute.lms.lmsapi.exception.AppException;
import com.hcmute.lms.lmsapi.exception.BusinessRuleViolationException;
import com.hcmute.lms.lmsapi.exception.ErrorCode;
import com.hcmute.lms.lmsapi.exception.ResourceNotFoundException;
import com.hcmute.lms.lmsapi.mapper.TermMapper;
import com.hcmute.lms.lmsapi.repository.CourseSectionRepository;
import com.hcmute.lms.lmsapi.repository.SyllabusRepository;
import com.hcmute.lms.lmsapi.repository.TermRepository;
import com.hcmute.lms.lmsapi.service.TermService;
import com.hcmute.lms.lmsapi.util.ObjectIdUtils;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TermServiceImpl implements TermService {

  private final TermRepository termRepository;
  private final SyllabusRepository syllabusRepository;
  private final CourseSectionRepository courseSectionRepository;
  private final TermMapper termMapper;

  @Override
  public List<TermResponse> getTerms() {
    return termMapper.toResponseList(termRepository.findAllByOrderByStartDateDesc());
  }

  @Override
  public TermResponse getTerm(String id) {
    return termMapper.toResponse(requireTerm(id));
  }

  @Override
  public TermResponse createTerm(TermRequest request) {
    String code = request.getCode().trim();
    if (termRepository.existsByCode(code)) {
      throw conflict(String.format("Mã học kỳ [%s] đã tồn tại trong hệ thống", code));
    }

    validateDateRange(request.getStartDate(), request.getEndDate());

    Term term = termMapper.toDocument(request);
    try {
      Term saved = termRepository.save(term);
      log.info("Tạo thành công học kỳ id=[{}] mã [{}]", saved.getId(), saved.getCode());
      return termMapper.toResponse(saved);
    } catch (DuplicateKeyException ex) {
      throw conflict(String.format("Mã học kỳ [%s] đã tồn tại trong hệ thống", code));
    }
  }

  @Override
  public TermResponse updateTerm(String id, TermUpdateRequest request) {
    Term term = requireTerm(id);

    Instant newStart =
        request.getStartDate() != null ? request.getStartDate() : term.getStartDate();
    Instant newEnd = request.getEndDate() != null ? request.getEndDate() : term.getEndDate();
    validateDateRange(newStart, newEnd);

    if (request.getAcademicYear() != null) {
      term.setAcademicYear(request.getAcademicYear().trim());
    }
    term.setStartDate(request.getStartDate());
    term.setEndDate(request.getEndDate());

    Term saved = termRepository.save(term);
    log.info("Cập nhật thông tin học kỳ id=[{}] mã [{}]", saved.getId(), saved.getCode());
    return termMapper.toResponse(saved);
  }

  @Override
  public TermResponse updateStatus(
      String id, TermStatusUpdateRequest request, String actorUsername) {
    Term term = requireTerm(id);
    TermStatus currentStatus = term.getStatus();
    TermStatus newStatus = request.getStatus();

    if (currentStatus == newStatus) {
      return termMapper.toResponse(term);
    }

    // US-18/AC-3: Mở lại học kỳ SCORE_LOCKED -> OPEN cần có lý do
    if (currentStatus == TermStatus.SCORE_LOCKED && newStatus == TermStatus.OPEN) {
      if (request.getReason() == null || request.getReason().trim().isEmpty()) {
        throw new BusinessRuleViolationException(
            "Cần nêu rõ lý do giải trình khi mở lại học kỳ đã khóa sổ điểm");
      }
    }

    term.setStatus(newStatus);
    Term saved = termRepository.save(term);
    log.info(
        "Chuyển trạng thái học kỳ id=[{}] từ [{}] sang [{}] bởi user=[{}] lý do=[{}]",
        saved.getId(),
        currentStatus,
        newStatus,
        actorUsername,
        request.getReason());
    return termMapper.toResponse(saved);
  }

  @Override
  public void deleteTerm(String id) {
    Term term = requireTerm(id);

    // Chặn xóa nếu đã có đề cương áp dụng học kỳ này
    if (syllabusRepository.existsByEffectiveFromTermIdOrEffectiveToTermId(id, id)) {
      throw new BusinessRuleViolationException(
          String.format(
              "Không thể xóa học kỳ mã [%s] vì đang được áp dụng trong đề cương môn học",
              term.getCode()));
    }

    // Chặn xóa nếu đã có lớp học phần mở trong học kỳ này
    if (courseSectionRepository.existsByTermId(id)) {
      throw new BusinessRuleViolationException(
          String.format("Không thể xóa học kỳ mã [%s] vì đang có lớp học phần", term.getCode()));
    }

    termRepository.delete(term);
    log.info("Xóa thành công học kỳ id=[{}] mã [{}]", id, term.getCode());
  }

  private Term requireTerm(String id) {
    ObjectIdUtils.requireValid(id, "termId");
    return termRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Học kỳ không tồn tại: " + id));
  }

  private void validateDateRange(Instant startDate, Instant endDate) {
    if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
      throw new BusinessRuleViolationException(
          "Thời điểm bắt đầu học kỳ (startDate) không được sau thời điểm kết thúc (endDate)");
    }
  }

  private AppException conflict(String message) {
    return new AppException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, message);
  }
}
