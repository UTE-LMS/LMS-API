package com.hcmute.lms.lmsapi.service.impl;

import com.hcmute.lms.lmsapi.document.Clo;
import com.hcmute.lms.lmsapi.document.CloMeasurement;
import com.hcmute.lms.lmsapi.document.Syllabus;
import com.hcmute.lms.lmsapi.dto.CloRequest;
import com.hcmute.lms.lmsapi.dto.CloResponse;
import com.hcmute.lms.lmsapi.dto.CloUpdateRequest;
import com.hcmute.lms.lmsapi.enums.SyllabusStatus;
import com.hcmute.lms.lmsapi.exception.AppException;
import com.hcmute.lms.lmsapi.exception.BusinessRuleViolationException;
import com.hcmute.lms.lmsapi.exception.ErrorCode;
import com.hcmute.lms.lmsapi.exception.ResourceNotFoundException;
import com.hcmute.lms.lmsapi.mapper.CloMapper;
import com.hcmute.lms.lmsapi.repository.CloPiLinkRepository;
import com.hcmute.lms.lmsapi.repository.CloRepository;
import com.hcmute.lms.lmsapi.repository.SyllabusRepository;
import com.hcmute.lms.lmsapi.service.CloService;
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
public class CloServiceImpl implements CloService {

  private final CloRepository cloRepository;
  private final SyllabusRepository syllabusRepository;
  private final CloPiLinkRepository cloPiLinkRepository;
  private final CloMapper cloMapper;

  @Override
  public List<CloResponse> getClos(String syllabusId) {
    requireSyllabus(syllabusId);
    return cloMapper.toResponseList(cloRepository.findBySyllabusIdOrderByCodeAsc(syllabusId));
  }

  @Override
  public CloResponse getClo(String id) {
    return cloMapper.toResponse(requireClo(id));
  }

  @Override
  public CloResponse createClo(String syllabusId, CloRequest request) {
    Syllabus syllabus = requireSyllabus(syllabusId);

    // US-10/AC-3: Đề cương CONFIRMED không cho sửa/thêm CLO
    if (syllabus.getStatus() == SyllabusStatus.CONFIRMED) {
      throw new BusinessRuleViolationException(
          "Đề cương đã CONFIRMED không cho phép thêm CLO mới; phải tạo phiên bản đề cương mới");
    }

    String code = request.getCode().trim();
    // US-11/AC-2: Hai CLO cùng code trong cùng đề cương -> 409
    if (cloRepository.existsBySyllabusIdAndCode(syllabusId, code)) {
      throw conflict(
          String.format("CLO với mã [%s] đã tồn tại trong đề cương id=[%s]", code, syllabusId));
    }

    Clo clo = cloMapper.toDocument(syllabusId, syllabus.getCourseId(), request);
    try {
      Clo saved = cloRepository.save(clo);
      log.info(
          "Tạo thành công CLO id=[{}] mã [{}] cho đề cương id=[{}]",
          saved.getId(),
          saved.getCode(),
          syllabusId);
      return cloMapper.toResponse(saved);
    } catch (DuplicateKeyException ex) {
      throw conflict(
          String.format("CLO với mã [%s] đã tồn tại trong đề cương id=[%s]", code, syllabusId));
    }
  }

  @Override
  public CloResponse updateClo(String id, CloUpdateRequest request) {
    Clo clo = requireClo(id);
    Syllabus syllabus = requireSyllabus(clo.getSyllabusId());

    // US-10/AC-3: Đề cương CONFIRMED không cho sửa CLO
    if (syllabus.getStatus() == SyllabusStatus.CONFIRMED) {
      throw new BusinessRuleViolationException(
          "Đề cương đã CONFIRMED không cho phép sửa nội dung CLO; phải tạo phiên bản đề cương mới");
    }

    String newCode = request.getCode().trim();
    if (!clo.getCode().equals(newCode)
        && cloRepository.existsBySyllabusIdAndCode(clo.getSyllabusId(), newCode)) {
      throw conflict(
          String.format(
              "CLO với mã [%s] đã tồn tại trong đề cương id=[%s]", newCode, clo.getSyllabusId()));
    }

    clo.setCode(newCode);
    clo.setContentOriginal(
        request.getContentOriginal() != null ? request.getContentOriginal().trim() : "");
    clo.setBloomLevel(request.getBloomLevel());
    clo.setBloomNote(request.getBloomNote() != null ? request.getBloomNote().trim() : null);

    if (request.getMeasurement() != null) {
      clo.setMeasurement(
          CloMeasurement.builder()
              .defaultWeight(request.getMeasurement().getDefaultWeight())
              .defaultPassThreshold(request.getMeasurement().getDefaultPassThreshold())
              .build());
    } else {
      clo.setMeasurement(null);
    }
    clo.setExtra(request.getExtra());

    try {
      Clo updated = cloRepository.save(clo);
      log.info("Cập nhật thành công CLO id=[{}] mã [{}]", updated.getId(), updated.getCode());
      return cloMapper.toResponse(updated);
    } catch (DuplicateKeyException ex) {
      throw conflict(
          String.format(
              "CLO với mã [%s] đã tồn tại trong đề cương id=[%s]", newCode, clo.getSyllabusId()));
    }
  }

  @Override
  public void deleteClo(String id) {
    Clo clo = requireClo(id);
    Syllabus syllabus = requireSyllabus(clo.getSyllabusId());

    // US-10/AC-3: Đề cương CONFIRMED không cho xóa CLO
    if (syllabus.getStatus() == SyllabusStatus.CONFIRMED) {
      throw new BusinessRuleViolationException(
          "Đề cương đã CONFIRMED không cho phép xóa CLO; phải tạo phiên bản đề cương mới");
    }

    // Restrict delete: Không xóa CLO nếu đã có liên kết CLO-PI
    if (cloPiLinkRepository.existsByCloId(id)) {
      throw new BusinessRuleViolationException(
          "Không thể xóa CLO đã được liên kết với PI trong ma trận CLO-PI");
    }

    cloRepository.deleteById(id);
    log.info("Đã xóa CLO id=[{}] khỏi đề cương id=[{}]", id, clo.getSyllabusId());
  }

  private Syllabus requireSyllabus(String syllabusId) {
    ObjectIdUtils.requireValid(syllabusId, "syllabusId");
    return syllabusRepository
        .findById(syllabusId)
        .orElseThrow(() -> new ResourceNotFoundException("Đề cương không tồn tại: " + syllabusId));
  }

  private Clo requireClo(String id) {
    ObjectIdUtils.requireValid(id, "cloId");
    return cloRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("CLO không tồn tại: " + id));
  }

  private AppException conflict(String message) {
    return new AppException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, message);
  }
}
