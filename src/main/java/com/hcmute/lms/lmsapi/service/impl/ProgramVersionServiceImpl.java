package com.hcmute.lms.lmsapi.service.impl;

import com.hcmute.lms.lmsapi.document.Pi;
import com.hcmute.lms.lmsapi.document.Plo;
import com.hcmute.lms.lmsapi.document.Program;
import com.hcmute.lms.lmsapi.document.ProgramVersion;
import com.hcmute.lms.lmsapi.document.Verification;
import com.hcmute.lms.lmsapi.dto.PageResponse;
import com.hcmute.lms.lmsapi.dto.ProgramVersionRequest;
import com.hcmute.lms.lmsapi.dto.ProgramVersionResponse;
import com.hcmute.lms.lmsapi.dto.ProgramVersionStatusUpdateRequest;
import com.hcmute.lms.lmsapi.dto.ProgramVersionUpdateRequest;
import com.hcmute.lms.lmsapi.enums.AuditAction;
import com.hcmute.lms.lmsapi.enums.ProgramVersionStatus;
import com.hcmute.lms.lmsapi.enums.VerificationStatus;
import com.hcmute.lms.lmsapi.exception.AppException;
import com.hcmute.lms.lmsapi.exception.BusinessRuleViolationException;
import com.hcmute.lms.lmsapi.exception.ErrorCode;
import com.hcmute.lms.lmsapi.exception.ResourceNotFoundException;
import com.hcmute.lms.lmsapi.mapper.ProgramVersionMapper;
import com.hcmute.lms.lmsapi.repository.PiRepository;
import com.hcmute.lms.lmsapi.repository.PloRepository;
import com.hcmute.lms.lmsapi.repository.ProgramRepository;
import com.hcmute.lms.lmsapi.repository.ProgramVersionRepository;
import com.hcmute.lms.lmsapi.service.AuditService;
import com.hcmute.lms.lmsapi.service.ProgramVersionService;
import com.hcmute.lms.lmsapi.util.MongoCountUtils;
import com.hcmute.lms.lmsapi.util.ObjectIdUtils;
import com.hcmute.lms.lmsapi.util.PageableValidationUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
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
  private final PloRepository ploRepository;
  private final PiRepository piRepository;
  private final ProgramVersionMapper programVersionMapper;
  private final MongoTemplate mongoTemplate;

  @Autowired(required = false)
  private AuditService auditService;

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
      List<ProgramVersionResponse> content = toResponses(versionPage.getContent());
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
      criteriaList.add(Criteria.where("applicableCohort").is(cleanCohort));
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
        toResponses(versions), pageable.getPageNumber(), pageable.getPageSize(), total);
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
    return toResponse(requireProgramVersion(id));
  }

  @Override
  public ProgramVersionResponse createProgramVersion(ProgramVersionRequest request) {
    ObjectIdUtils.requireValid(request.getProgramId(), "programId");
    Program program =
        programRepository
            .findById(request.getProgramId())
            .orElseThrow(
                () ->
                    new BusinessRuleViolationException(
                        "PROGRAM_VERSION_PROGRAM",
                        "Chương trình đào tạo không tồn tại: " + request.getProgramId()));

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
            .applicableCohort(trimToNull(request.getApplicableCohort()))
            .status(ProgramVersionStatus.ACTIVE)
            .verification(Verification.builder().status(VerificationStatus.VERIFIED).build())
            .build();

    try {
      programVersion = programVersionRepository.save(programVersion);
    } catch (DuplicateKeyException ex) {
      log.warn("Duplicate program version on create: {} - {}", request.getProgramId(), version);
      throw conflict(version);
    }
    AuditLogHelper.record(
        auditService,
        AuditAction.CREATE,
        "program_versions",
        programVersion.getId(),
        null,
        programVersion,
        null);
    return programVersionMapper.toResponse(programVersion, program, 0L, 0L);
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
    programVersion.setApplicableCohort(trimToNull(request.getApplicableCohort()));

    try {
      programVersion = programVersionRepository.save(programVersion);
    } catch (DuplicateKeyException ex) {
      log.warn(
          "Duplicate program version on update: {} - {}", programVersion.getProgramId(), version);
      throw conflict(version);
    }
    AuditLogHelper.record(
        auditService,
        AuditAction.UPDATE,
        "program_versions",
        programVersion.getId(),
        null,
        programVersion,
        null);
    return toResponse(programVersion);
  }

  @Override
  public ProgramVersionResponse updateProgramVersionStatus(
      String id, ProgramVersionStatusUpdateRequest request) {
    ProgramVersion programVersion = requireProgramVersion(id);

    validateStatusTransition(programVersion.getStatus(), request.getStatus());
    programVersion.setStatus(request.getStatus());

    programVersion = programVersionRepository.save(programVersion);
    AuditLogHelper.record(
        auditService,
        AuditAction.STATUS_CHANGE,
        "program_versions",
        programVersion.getId(),
        null,
        programVersion,
        null);
    return toResponse(programVersion);
  }

  private List<ProgramVersionResponse> toResponses(List<ProgramVersion> versions) {
    if (versions == null || versions.isEmpty()) {
      return List.of();
    }
    Set<String> programIds =
        versions.stream()
            .map(ProgramVersion::getProgramId)
            .filter(id -> id != null && !id.isBlank())
            .collect(Collectors.toCollection(LinkedHashSet::new));
    Map<String, Program> programMap =
        programIds.isEmpty()
            ? Collections.emptyMap()
            : programRepository.findAllById(programIds).stream()
                .collect(Collectors.toMap(Program::getId, Function.identity(), (a, b) -> a));

    List<String> versionIds =
        versions.stream().map(ProgramVersion::getId).filter(Objects::nonNull).toList();
    Map<String, Long> ploCounts =
        MongoCountUtils.countByObjectIdField(
            mongoTemplate, Plo.class, "programVersionId", versionIds);
    Map<String, Long> piCounts =
        MongoCountUtils.countByObjectIdField(
            mongoTemplate, Pi.class, "programVersionId", versionIds);

    return versions.stream()
        .map(
            pv ->
                programVersionMapper.toResponse(
                    pv,
                    pv.getProgramId() != null ? programMap.get(pv.getProgramId()) : null,
                    ploCounts.getOrDefault(pv.getId(), 0L),
                    piCounts.getOrDefault(pv.getId(), 0L)))
        .toList();
  }

  private ProgramVersionResponse toResponse(ProgramVersion pv) {
    Program program =
        pv.getProgramId() != null && !pv.getProgramId().isBlank()
            ? programRepository.findById(pv.getProgramId()).orElse(null)
            : null;
    long ploCount = ploRepository.countByProgramVersionId(pv.getId());
    long piCount = piRepository.countByProgramVersionId(pv.getId());
    return programVersionMapper.toResponse(pv, program, ploCount, piCount);
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
            ? next == ProgramVersionStatus.ACTIVE
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
