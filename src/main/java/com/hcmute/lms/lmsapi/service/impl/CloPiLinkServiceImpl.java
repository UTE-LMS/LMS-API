package com.hcmute.lms.lmsapi.service.impl;

import com.hcmute.lms.lmsapi.document.Clo;
import com.hcmute.lms.lmsapi.document.CloPiLink;
import com.hcmute.lms.lmsapi.document.Pi;
import com.hcmute.lms.lmsapi.document.Syllabus;
import com.hcmute.lms.lmsapi.document.Verification;
import com.hcmute.lms.lmsapi.dto.CloPiLinkCellDto;
import com.hcmute.lms.lmsapi.dto.CloPiLinkRequest;
import com.hcmute.lms.lmsapi.dto.CloPiLinkResponse;
import com.hcmute.lms.lmsapi.dto.CloPiMatrixResponse;
import com.hcmute.lms.lmsapi.dto.CloResponse;
import com.hcmute.lms.lmsapi.dto.PiResponse;
import com.hcmute.lms.lmsapi.dto.UserPrincipal;
import com.hcmute.lms.lmsapi.dto.VerificationRequest;
import com.hcmute.lms.lmsapi.enums.AccessType;
import com.hcmute.lms.lmsapi.exception.AppException;
import com.hcmute.lms.lmsapi.exception.BusinessRuleViolationException;
import com.hcmute.lms.lmsapi.exception.ErrorCode;
import com.hcmute.lms.lmsapi.exception.ResourceNotFoundException;
import com.hcmute.lms.lmsapi.mapper.CloMapper;
import com.hcmute.lms.lmsapi.mapper.CloPiLinkMapper;
import com.hcmute.lms.lmsapi.mapper.PiMapper;
import com.hcmute.lms.lmsapi.repository.CloPiLinkRepository;
import com.hcmute.lms.lmsapi.repository.CloRepository;
import com.hcmute.lms.lmsapi.repository.PiRepository;
import com.hcmute.lms.lmsapi.repository.ProgramCourseRepository;
import com.hcmute.lms.lmsapi.repository.ProgramVersionRepository;
import com.hcmute.lms.lmsapi.repository.SyllabusRepository;
import com.hcmute.lms.lmsapi.service.CloPiLinkService;
import com.hcmute.lms.lmsapi.service.ScopeAuthorizationService;
import com.hcmute.lms.lmsapi.util.ObjectIdUtils;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CloPiLinkServiceImpl implements CloPiLinkService {

  private final CloPiLinkRepository cloPiLinkRepository;
  private final SyllabusRepository syllabusRepository;
  private final CloRepository cloRepository;
  private final PiRepository piRepository;
  private final ProgramVersionRepository programVersionRepository;
  private final ProgramCourseRepository programCourseRepository;
  private final ScopeAuthorizationService scopeAuthorizationService;
  private final CloPiLinkMapper cloPiLinkMapper;
  private final CloMapper cloMapper;
  private final PiMapper piMapper;

  @Override
  public List<CloPiLinkResponse> getLinks(String syllabusId) {
    requireSyllabus(syllabusId);
    return cloPiLinkMapper.toResponseList(cloPiLinkRepository.findBySyllabusId(syllabusId));
  }

  @Override
  public CloPiLinkResponse createLink(String syllabusId, CloPiLinkRequest request) {
    Syllabus syllabus = requireSyllabus(syllabusId);

    ObjectIdUtils.requireValid(request.getProgramVersionId(), "programVersionId");
    if (!programVersionRepository.existsById(request.getProgramVersionId())) {
      throw new ResourceNotFoundException(
          "Phiên bản CTĐT không tồn tại: " + request.getProgramVersionId());
    }

    // TD-28: Kiểm tra học phần của đề cương phải thuộc phiên bản CTĐT được liên kết
    // (program_courses)
    if (syllabus.getCourseId() != null
        && !programCourseRepository.existsByProgramVersionIdAndCourseId(
            request.getProgramVersionId(), syllabus.getCourseId())) {
      throw new BusinessRuleViolationException(
          String.format(
              "Học phần id=[%s] của đề cương không thuộc phiên bản CTĐT id=[%s]",
              syllabus.getCourseId(), request.getProgramVersionId()));
    }

    Clo clo = requireClo(request.getCloId());
    if (!syllabusId.equals(clo.getSyllabusId())) {
      throw new BusinessRuleViolationException(
          String.format(
              "CLO id=[%s] không thuộc về đề cương id=[%s]", request.getCloId(), syllabusId));
    }

    Pi pi = requirePi(request.getPiId());
    // US-12/AC-2: PI khác programVersionId -> 422
    if (!request.getProgramVersionId().equals(pi.getProgramVersionId())) {
      throw new BusinessRuleViolationException(
          String.format(
              "PI id=[%s] không thuộc về phiên bản CTĐT id=[%s]",
              request.getPiId(), request.getProgramVersionId()));
    }

    // US-12/AC-2: Trùng (cloId, piId) -> 409
    if (cloPiLinkRepository.existsByCloIdAndPiId(request.getCloId(), request.getPiId())) {
      throw conflict(
          String.format(
              "Liên kết giữa CLO id=[%s] và PI id=[%s] đã tồn tại",
              request.getCloId(), request.getPiId()));
    }

    CloPiLink link = cloPiLinkMapper.toDocument(syllabusId, request);
    try {
      CloPiLink saved = cloPiLinkRepository.save(link);
      log.info(
          "Tạo thành công liên kết CLO-PI id=[{}] giữa CLO [{}] và PI [{}]",
          saved.getId(),
          request.getCloId(),
          request.getPiId());
      return cloPiLinkMapper.toResponse(saved);
    } catch (DuplicateKeyException ex) {
      throw conflict(
          String.format(
              "Liên kết giữa CLO id=[%s] và PI id=[%s] đã tồn tại",
              request.getCloId(), request.getPiId()));
    }
  }

  @Override
  public void deleteLink(String id) {
    ObjectIdUtils.requireValid(id, "cloPiLinkId");
    if (!cloPiLinkRepository.existsById(id)) {
      throw new ResourceNotFoundException("Liên kết CLO-PI không tồn tại: " + id);
    }
    cloPiLinkRepository.deleteById(id);
    log.info("Đã xóa liên kết CLO-PI id=[{}]", id);
  }

  @Override
  public CloPiMatrixResponse getMatrix(String syllabusId, String programVersionId) {
    requireSyllabus(syllabusId);
    ObjectIdUtils.requireValid(programVersionId, "programVersionId");
    if (!programVersionRepository.existsById(programVersionId)) {
      throw new ResourceNotFoundException("Phiên bản CTĐT không tồn tại: " + programVersionId);
    }

    List<Clo> clos = cloRepository.findBySyllabusIdOrderByCodeAsc(syllabusId);
    List<Pi> pis = piRepository.findByProgramVersionIdOrderByDisplayOrderAsc(programVersionId);
    List<CloPiLink> links =
        cloPiLinkRepository.findBySyllabusIdAndProgramVersionId(syllabusId, programVersionId);

    List<CloResponse> cloResponses = cloMapper.toResponseList(clos);
    List<PiResponse> piResponses = piMapper.toResponseList(pis);

    List<CloPiLinkCellDto> cellDtos = new ArrayList<>();
    for (CloPiLink link : links) {
      cellDtos.add(
          CloPiLinkCellDto.builder()
              .linkId(link.getId())
              .cloId(link.getCloId())
              .piId(link.getPiId())
              .weight(link.getWeight())
              .verification(link.getVerification())
              .build());
    }

    return CloPiMatrixResponse.builder()
        .syllabusId(syllabusId)
        .programVersionId(programVersionId)
        .clos(cloResponses)
        .pis(piResponses)
        .links(cellDtos)
        .build();
  }

  @Override
  public CloPiLinkResponse verifyLink(
      String id, VerificationRequest request, UserPrincipal principal) {
    ObjectIdUtils.requireValid(id, "cloPiLinkId");
    CloPiLink link =
        cloPiLinkRepository
            .findById(id)
            .orElseThrow(
                () -> new ResourceNotFoundException("Liên kết CLO-PI không tồn tại: " + id));

    // Thẩm định quyền QA theo scope
    if (!scopeAuthorizationService.canAccessProgramVersion(
        principal, link.getProgramVersionId(), AccessType.WRITE)) {
      throw new AppException(
          ErrorCode.FORBIDDEN,
          HttpStatus.FORBIDDEN,
          "Chuyên viên QA không có quyền thẩm định trong phạm vi phiên bản CTĐT này");
    }

    Verification verification =
        Verification.builder()
            .status(request.getStatus())
            .verifiedBy(principal != null ? principal.getId() : null)
            .verifiedAt(Instant.now())
            .note(request.getNote())
            .build();

    link.setVerification(verification);
    CloPiLink saved = cloPiLinkRepository.save(link);
    log.info(
        "Xác nhận liên kết CLO-PI id=[{}] sang trạng thái [{}] bởi user=[{}]",
        id,
        request.getStatus(),
        principal != null ? principal.getId() : "system");
    return cloPiLinkMapper.toResponse(saved);
  }

  private Syllabus requireSyllabus(String syllabusId) {
    ObjectIdUtils.requireValid(syllabusId, "syllabusId");
    return syllabusRepository
        .findById(syllabusId)
        .orElseThrow(() -> new ResourceNotFoundException("Đề cương không tồn tại: " + syllabusId));
  }

  private Clo requireClo(String cloId) {
    ObjectIdUtils.requireValid(cloId, "cloId");
    return cloRepository
        .findById(cloId)
        .orElseThrow(() -> new ResourceNotFoundException("CLO không tồn tại: " + cloId));
  }

  private Pi requirePi(String piId) {
    ObjectIdUtils.requireValid(piId, "piId");
    return piRepository
        .findById(piId)
        .orElseThrow(() -> new ResourceNotFoundException("PI không tồn tại: " + piId));
  }

  private AppException conflict(String message) {
    return new AppException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, message);
  }
}
