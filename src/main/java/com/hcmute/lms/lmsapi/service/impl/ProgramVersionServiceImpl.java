package com.hcmute.lms.lmsapi.service.impl;

import com.hcmute.lms.lmsapi.document.Program;
import com.hcmute.lms.lmsapi.document.ProgramVersion;
import com.hcmute.lms.lmsapi.document.Verification;
import com.hcmute.lms.lmsapi.dto.PageResponse;
import com.hcmute.lms.lmsapi.dto.ProgramVersionRequest;
import com.hcmute.lms.lmsapi.dto.ProgramVersionResponse;
import com.hcmute.lms.lmsapi.dto.ProgramVersionStatusUpdateRequest;
import com.hcmute.lms.lmsapi.dto.ProgramVersionUpdateRequest;
import com.hcmute.lms.lmsapi.enums.ProgramVersionStatus;
import com.hcmute.lms.lmsapi.enums.VerificationStatus;
import com.hcmute.lms.lmsapi.exception.AppException;
import com.hcmute.lms.lmsapi.exception.BusinessRuleViolationException;
import com.hcmute.lms.lmsapi.exception.ErrorCode;
import com.hcmute.lms.lmsapi.exception.ResourceNotFoundException;
import com.hcmute.lms.lmsapi.mapper.ProgramVersionMapper;
import com.hcmute.lms.lmsapi.repository.ProgramRepository;
import com.hcmute.lms.lmsapi.repository.ProgramVersionRepository;
import com.hcmute.lms.lmsapi.service.ProgramVersionService;
import com.hcmute.lms.lmsapi.util.ObjectIdUtils;
import com.hcmute.lms.lmsapi.util.PageableValidationUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProgramVersionServiceImpl implements ProgramVersionService {

  private final ProgramVersionRepository programVersionRepository;
  private final ProgramRepository programRepository;
  private final ProgramVersionMapper programVersionMapper;
  private final MongoTemplate mongoTemplate;

  @Override
  public PageResponse<ProgramVersionResponse> getProgramVersions(
      int page, int size, String programId) {
    return getProgramVersions(page, size, programId, null, null, null, null);
  }

  @Override
  public PageResponse<ProgramVersionResponse> getProgramVersions(
      int page,
      int size,
      String programId,
      String facultyId,
      String cohort,
      ProgramVersionStatus status,
      String search) {
    PageableValidationUtils.validate(page, size);
    Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "version"));

    String cleanProgramId = trimToNull(programId);
    String cleanFacultyId = trimToNull(facultyId);
    String cleanCohort = trimToNull(cohort);
    String cleanSearch = trimToNull(search);

    if (cleanFacultyId == null && cleanCohort == null && status == null && cleanSearch == null) {
      Page<ProgramVersion> versionPage;
      if (cleanProgramId == null) {
        versionPage = programVersionRepository.findAll(pageable);
      } else {
        ObjectIdUtils.requireValid(cleanProgramId, "programId");
        versionPage = programVersionRepository.findByProgramId(cleanProgramId, pageable);
      }
      List<ProgramVersionResponse> content =
          programVersionMapper.toResponseList(versionPage.getContent());
      return PageResponse.of(
          content, versionPage.getNumber(), versionPage.getSize(), versionPage.getTotalElements());
    }

    List<Criteria> criteriaList = new ArrayList<>();
    if (cleanProgramId != null) {
      ObjectIdUtils.requireValid(cleanProgramId, "programId");
      criteriaList.add(Criteria.where("programId").is(new ObjectId(cleanProgramId)));
    }
    if (cleanFacultyId != null) {
      ObjectIdUtils.requireValid(cleanFacultyId, "facultyId");
      List<ObjectId> programObjectIds =
          programRepository.findByFacultyId(cleanFacultyId).stream()
              .map(Program::getId)
              .map(ObjectId::new)
              .toList();
      if (programObjectIds.isEmpty()) {
        return PageResponse.of(List.of(), pageable.getPageNumber(), pageable.getPageSize(), 0);
      }
      criteriaList.add(Criteria.where("programId").in(programObjectIds));
    }
    if (cleanCohort != null) {
      criteriaList.add(Criteria.where("applicableCohorts").is(cleanCohort));
    }
    if (status != null) {
      criteriaList.add(Criteria.where("status").is(status));
    }
    if (cleanSearch != null) {
      criteriaList.add(Criteria.where("version").regex(Pattern.quote(cleanSearch), "i"));
    }

    Query query = new Query(new Criteria().andOperator(criteriaList.toArray(Criteria[]::new)));
    long total = mongoTemplate.count(Query.of(query).limit(-1).skip(-1), ProgramVersion.class);
    query.with(pageable);
    List<ProgramVersion> versions = mongoTemplate.find(query, ProgramVersion.class);
    return PageResponse.of(
        programVersionMapper.toResponseList(versions),
        pageable.getPageNumber(),
        pageable.getPageSize(),
        total);
  }

  private String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  @Override
  public ProgramVersionResponse getProgramVersionById(String id) {
    return programVersionMapper.toResponse(requireProgramVersion(id));
  }

  @Override
  public ProgramVersionResponse createProgramVersion(ProgramVersionRequest request) {
    ObjectIdUtils.requireValid(request.getProgramId(), "programId");
    if (!programRepository.existsById(request.getProgramId())) {
      throw new BusinessRuleViolationException(
          "PROGRAM_VERSION_PROGRAM",
          "Chương trình đào tạo không tồn tại: " + request.getProgramId());
    }

    String version = request.getVersion().trim();
    if (programVersionRepository.existsByProgramIdAndVersion(request.getProgramId(), version)) {
      throw conflict(version);
    }

    ProgramVersion programVersion =
        ProgramVersion.builder()
            .programId(request.getProgramId())
            .version(version)
            .totalCredits(request.getTotalCredits())
            .durationYears(request.getDurationYears())
            .applicableCohorts(
                request.getApplicableCohorts() != null
                    ? new ArrayList<>(request.getApplicableCohorts())
                    : new ArrayList<>())
            .status(ProgramVersionStatus.DRAFT)
            .verification(Verification.builder().status(VerificationStatus.VERIFIED).build())
            .build();

    try {
      programVersion = programVersionRepository.save(programVersion);
    } catch (DuplicateKeyException ex) {
      log.warn("Duplicate program version on create: {} - {}", request.getProgramId(), version);
      throw conflict(version);
    }
    return programVersionMapper.toResponse(programVersion);
  }

  @Override
  public ProgramVersionResponse updateProgramVersion(
      String id, ProgramVersionUpdateRequest request) {
    ProgramVersion programVersion = requireProgramVersion(id);

    String version = request.getVersion().trim();
    if (!version.equals(programVersion.getVersion())
        && programVersionRepository.existsByProgramIdAndVersionAndIdNot(
            programVersion.getProgramId(), version, id)) {
      throw conflict(version);
    }

    programVersion.setVersion(version);
    programVersion.setTotalCredits(request.getTotalCredits());
    programVersion.setDurationYears(request.getDurationYears());
    programVersion.setApplicableCohorts(
        request.getApplicableCohorts() != null
            ? new ArrayList<>(request.getApplicableCohorts())
            : new ArrayList<>());

    try {
      programVersion = programVersionRepository.save(programVersion);
    } catch (DuplicateKeyException ex) {
      log.warn(
          "Duplicate program version on update: {} - {}", programVersion.getProgramId(), version);
      throw conflict(version);
    }
    return programVersionMapper.toResponse(programVersion);
  }

  @Override
  public ProgramVersionResponse updateProgramVersionStatus(
      String id, ProgramVersionStatusUpdateRequest request) {
    ProgramVersion programVersion = requireProgramVersion(id);

    validateStatusTransition(programVersion.getStatus(), request.getStatus());
    programVersion.setStatus(request.getStatus());

    programVersion = programVersionRepository.save(programVersion);
    return programVersionMapper.toResponse(programVersion);
  }

  private ProgramVersion requireProgramVersion(String id) {
    ObjectIdUtils.requireValid(id, "id");
    return programVersionRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("ProgramVersion", "id", id));
  }

  private void validateStatusTransition(ProgramVersionStatus current, ProgramVersionStatus next) {
    if (next == null || next == current) {
      return;
    }
    boolean allowed =
        current == null
            ? next == ProgramVersionStatus.DRAFT
            : next.ordinal() == current.ordinal() + 1;
    if (!allowed) {
      throw invalidTransition(current, next);
    }
  }

  private BusinessRuleViolationException invalidTransition(
      ProgramVersionStatus current, ProgramVersionStatus next) {
    return new BusinessRuleViolationException(
        "PROGRAM_VERSION_STATUS",
        String.format(
            "Không thể chuyển trạng thái từ %s sang %s; chỉ cho phép DRAFT -> ACTIVE -> ARCHIVED",
            current, next));
  }

  private AppException conflict(String version) {
    return new AppException(
        ErrorCode.CONFLICT,
        HttpStatus.CONFLICT,
        "Phiên bản chương trình đào tạo đã tồn tại: " + version);
  }
}
