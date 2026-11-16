package com.hcmute.lms.lmsapi.service.impl;

import com.hcmute.lms.lmsapi.document.AuditEntity;
import com.hcmute.lms.lmsapi.document.Course;
import com.hcmute.lms.lmsapi.document.Pi;
import com.hcmute.lms.lmsapi.document.Plo;
import com.hcmute.lms.lmsapi.document.ProgramCourse;
import com.hcmute.lms.lmsapi.document.ProgramVersion;
import com.hcmute.lms.lmsapi.document.Verification;
import com.hcmute.lms.lmsapi.dto.AssessmentScoreTemplate;
import com.hcmute.lms.lmsapi.dto.ProgramCsvImportResponse;
import com.hcmute.lms.lmsapi.enums.AuditAction;
import com.hcmute.lms.lmsapi.enums.PloCategory;
import com.hcmute.lms.lmsapi.enums.ProgramVersionStatus;
import com.hcmute.lms.lmsapi.enums.VerificationStatus;
import com.hcmute.lms.lmsapi.exception.BusinessRuleViolationException;
import com.hcmute.lms.lmsapi.exception.ResourceNotFoundException;
import com.hcmute.lms.lmsapi.mapper.PiMapper;
import com.hcmute.lms.lmsapi.mapper.PloMapper;
import com.hcmute.lms.lmsapi.repository.CourseRepository;
import com.hcmute.lms.lmsapi.repository.PiRepository;
import com.hcmute.lms.lmsapi.repository.PloRepository;
import com.hcmute.lms.lmsapi.repository.ProgramCourseRepository;
import com.hcmute.lms.lmsapi.repository.ProgramRepository;
import com.hcmute.lms.lmsapi.repository.ProgramVersionRepository;
import com.hcmute.lms.lmsapi.service.AuditService;
import com.hcmute.lms.lmsapi.service.ProgramImportService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ProgramImportServiceImpl implements ProgramImportService {

  private static final double WEIGHT_TOLERANCE = 1e-6;

  private final ProgramRepository programRepository;
  private final ProgramVersionRepository programVersionRepository;
  private final PloRepository ploRepository;
  private final PiRepository piRepository;
  private final ProgramCourseRepository programCourseRepository;
  private final CourseRepository courseRepository;
  private final PloMapper ploMapper;
  private final PiMapper piMapper;
  private final AuditService auditService;

  @Override
  public AssessmentScoreTemplate generateTemplateCsv() {
    String csv =
        "\uFEFF"
            + "ploCode,ploName,ploPassThreshold,ploDisplayOrder,ploCapabilityLevel,ploCategory,piCode,piName,piWeight,piPassThreshold,piCapabilityLevel,courseCodes\r\n"
            + "PLO1,Kiến thức nền tảng,0.6,1,3,GENERAL,PI1.1,Vận dụng toán học,0.5,0.6,3,OOPR230279;PRPR130179\r\n"
            + "PLO1,Kiến thức nền tảng,0.6,1,3,GENERAL,PI1.2,Vận dụng cơ sở ngành,0.5,0.6,3,OOPR230279\r\n";
    return new AssessmentScoreTemplate(
        "program-version-template.csv", csv.getBytes(StandardCharsets.UTF_8));
  }

  @Override
  @Transactional
  public ProgramCsvImportResponse importProgramCsv(
      String programId,
      String version,
      Integer totalCredits,
      Double durationYears,
      String applicableCohort,
      MultipartFile file,
      String actorId) {
    if (!programRepository.existsById(programId)) {
      throw new ResourceNotFoundException("Không tìm thấy CTĐT với id: " + programId);
    }
    String trimmedVersion = version == null ? "" : version.trim();
    if (trimmedVersion.isEmpty()) {
      throw new BusinessRuleViolationException("Mã phiên bản CTĐT không được để trống");
    }

    CsvTextParser.ParsedCsv parsed = CsvTextParser.read(file);
    CsvTextParser.requireColumns(
        parsed.headerIndex(),
        "ploCode",
        "ploPassThreshold",
        "piCode",
        "piWeight",
        "piPassThreshold");

    ParsedProgramData data = parseProgramRows(parsed);
    String cleanCohort =
        applicableCohort != null && !applicableCohort.trim().isEmpty()
            ? applicableCohort.trim()
            : null;
    ProgramVersion pv =
        resolveOrResetDraftVersion(
            programId, trimmedVersion, totalCredits, durationYears, cleanCohort);

    Verification verified = Verification.builder().status(VerificationStatus.VERIFIED).build();

    List<Plo> savedPlos = savePlos(pv.getId(), data, verified);
    Map<String, Plo> ploByCode = new LinkedHashMap<>();
    for (Plo plo : savedPlos) {
      ploByCode.put(plo.getCode(), plo);
    }

    List<Pi> savedPis = savePis(pv.getId(), data, ploByCode, verified);
    int courseCount = saveProgramCourses(pv.getId(), data.courseCodes(), data.warnings());

    return ProgramCsvImportResponse.builder()
        .programVersionId(pv.getId())
        .programId(programId)
        .version(pv.getVersion())
        .totalCredits(pv.getTotalCredits())
        .durationYears(pv.getDurationYears())
        .applicableCohort(pv.getApplicableCohort())
        .status(pv.getStatus())
        .verificationStatus(VerificationStatus.VERIFIED)
        .ploCount(savedPlos.size())
        .piCount(savedPis.size())
        .courseCount(courseCount)
        .warnings(data.warnings())
        .plos(ploMapper.toResponseList(savedPlos))
        .pis(piMapper.toResponseList(savedPis))
        .build();
  }

  @Override
  @Transactional
  public ProgramCsvImportResponse confirmImport(String programVersionId, String actorId) {
    ProgramVersion pv =
        programVersionRepository
            .findById(programVersionId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Không tìm thấy phiên bản CTĐT: " + programVersionId));

    List<ProgramVersion> activeVersions =
        programVersionRepository.findByProgramIdAndStatus(
            pv.getProgramId(), ProgramVersionStatus.ACTIVE);
    for (ProgramVersion other : activeVersions) {
      if (!other.getId().equals(pv.getId())) {
        other.setStatus(ProgramVersionStatus.ARCHIVED);
        programVersionRepository.save(other);
      }
    }

    Verification verified =
        Verification.builder()
            .status(VerificationStatus.VERIFIED)
            .verifiedBy(actorId)
            .verifiedAt(Instant.now())
            .note("Xác nhận từ luồng Import CSV CTĐT")
            .build();

    pv.setStatus(ProgramVersionStatus.ACTIVE);
    pv.setVerification(verified);
    ProgramVersion savedPv = programVersionRepository.save(pv);

    List<Plo> plos = ploRepository.findByProgramVersionIdOrderByDisplayOrderAsc(programVersionId);
    for (Plo plo : plos) {
      plo.setVerification(verified);
    }
    ploRepository.saveAll(plos);

    List<Pi> pis = piRepository.findByProgramVersionIdOrderByDisplayOrderAsc(programVersionId);
    for (Pi pi : pis) {
      pi.setVerification(verified);
    }
    piRepository.saveAll(pis);

    List<ProgramCourse> courses =
        programCourseRepository.findByProgramVersionIdOrderBySemesterAscCourseIdAsc(
            programVersionId);

    auditService.record(
        AuditAction.IMPORT_CONFIRM,
        AuditEntity.builder().collection("program_versions").id(savedPv.getId()).build(),
        null,
        savedPv,
        "Xác nhận import phiên bản CTĐT " + savedPv.getVersion(),
        actorId,
        null);

    return ProgramCsvImportResponse.builder()
        .programVersionId(savedPv.getId())
        .programId(savedPv.getProgramId())
        .version(savedPv.getVersion())
        .totalCredits(savedPv.getTotalCredits())
        .durationYears(savedPv.getDurationYears())
        .applicableCohort(savedPv.getApplicableCohort())
        .status(savedPv.getStatus())
        .verificationStatus(VerificationStatus.VERIFIED)
        .ploCount(plos.size())
        .piCount(pis.size())
        .courseCount(courses.size())
        .warnings(List.of())
        .plos(ploMapper.toResponseList(plos))
        .pis(piMapper.toResponseList(pis))
        .build();
  }

  private ProgramVersion resolveOrResetDraftVersion(
      String programId,
      String version,
      Integer totalCredits,
      Double durationYears,
      String applicableCohort) {
    Optional<ProgramVersion> existing =
        programVersionRepository.findByProgramIdAndVersion(programId, version);
    if (existing.isPresent()) {
      ProgramVersion pv = existing.get();
      if (pv.getStatus() == ProgramVersionStatus.ARCHIVED) {
        throw new BusinessRuleViolationException(
            "Phiên bản " + version + " đã được lưu trữ (ARCHIVED), hãy nhập với mã phiên bản mới");
      }
      piRepository.deleteByProgramVersionId(pv.getId());
      ploRepository.deleteByProgramVersionId(pv.getId());
      programCourseRepository.deleteByProgramVersionId(pv.getId());
      if (totalCredits != null) {
        pv.setTotalCredits(totalCredits);
      }
      if (durationYears != null) {
        pv.setDurationYears(durationYears);
      }
      if (applicableCohort != null) {
        pv.setApplicableCohort(applicableCohort);
      }
      pv.setVerification(Verification.builder().status(VerificationStatus.VERIFIED).build());
      return programVersionRepository.save(pv);
    }
    return programVersionRepository.save(
        ProgramVersion.builder()
            .programId(programId)
            .version(version)
            .totalCredits(totalCredits)
            .durationYears(durationYears)
            .applicableCohort(applicableCohort)
            .status(ProgramVersionStatus.ACTIVE)
            .verification(Verification.builder().status(VerificationStatus.VERIFIED).build())
            .build());
  }

  private ParsedProgramData parseProgramRows(CsvTextParser.ParsedCsv parsed) {
    Map<String, PloDraft> plos = new LinkedHashMap<>();
    List<PiDraft> pis = new ArrayList<>();
    Set<String> seenPiCodes = new LinkedHashSet<>();
    Set<String> courseCodes = new LinkedHashSet<>();
    List<String> warnings = new ArrayList<>();

    for (int i = 0; i < parsed.rows().size(); i++) {
      int rowNum = i + 2;
      List<String> row = parsed.rows().get(i);
      String ploCode = CsvTextParser.cell(parsed.headerIndex(), row, "ploCode");
      String piCode = CsvTextParser.cell(parsed.headerIndex(), row, "piCode");
      if (ploCode.isEmpty() || piCode.isEmpty()) {
        throw new BusinessRuleViolationException(
            "Dòng " + rowNum + ": ploCode và piCode không được để trống");
      }
      double ploThreshold =
          CsvTextParser.parseUnitDouble(
              CsvTextParser.cell(parsed.headerIndex(), row, "ploPassThreshold"),
              "ploPassThreshold",
              rowNum);
      double piWeight =
          CsvTextParser.parseUnitDouble(
              CsvTextParser.cell(parsed.headerIndex(), row, "piWeight"), "piWeight", rowNum);
      double piThreshold =
          CsvTextParser.parseUnitDouble(
              CsvTextParser.cell(parsed.headerIndex(), row, "piPassThreshold"),
              "piPassThreshold",
              rowNum);

      String ploName = CsvTextParser.cell(parsed.headerIndex(), row, "ploName");
      int ploOrder =
          CsvTextParser.parseIntOrDefault(
              CsvTextParser.cell(parsed.headerIndex(), row, "ploDisplayOrder"), plos.size() + 1);
      Integer ploCapabilityLevel =
          parseCapabilityLevel(
              CsvTextParser.cell(parsed.headerIndex(), row, "ploCapabilityLevel"),
              "ploCapabilityLevel",
              rowNum);
      PloCategory ploCategory =
          parsePloCategory(CsvTextParser.cell(parsed.headerIndex(), row, "ploCategory"), rowNum);
      plos.putIfAbsent(
          ploCode,
          new PloDraft(ploCode, ploName, ploThreshold, ploOrder, ploCapabilityLevel, ploCategory));

      if (!seenPiCodes.add(piCode)) {
        throw new BusinessRuleViolationException(
            "Dòng " + rowNum + ": mã PI bị trùng trong tệp: " + piCode);
      }
      String piName = CsvTextParser.cell(parsed.headerIndex(), row, "piName");
      Integer piCapabilityLevel =
          parseCapabilityLevel(
              CsvTextParser.cell(parsed.headerIndex(), row, "piCapabilityLevel"),
              "piCapabilityLevel",
              rowNum);
      pis.add(
          new PiDraft(ploCode, piCode, piName, piWeight, piThreshold, piCapabilityLevel, i + 1));

      String rawCourses = CsvTextParser.cell(parsed.headerIndex(), row, "courseCodes");
      if (!rawCourses.isEmpty()) {
        for (String token : rawCourses.split("[;|]")) {
          String code = token.trim();
          if (!code.isEmpty()) {
            courseCodes.add(code);
          }
        }
      }
    }

    validatePloWeightSums(plos.keySet(), pis);
    return new ParsedProgramData(new ArrayList<>(plos.values()), pis, courseCodes, warnings);
  }

  private void validatePloWeightSums(Set<String> ploCodes, List<PiDraft> pis) {
    Map<String, Double> sums = new LinkedHashMap<>();
    for (String code : ploCodes) {
      sums.put(code, 0.0);
    }
    for (PiDraft pi : pis) {
      sums.computeIfPresent(pi.ploCode(), (k, v) -> v + pi.weight());
    }
    for (Map.Entry<String, Double> entry : sums.entrySet()) {
      if (Math.abs(entry.getValue() - 1.0) > WEIGHT_TOLERANCE) {
        throw new BusinessRuleViolationException(
            "Tổng trọng số PI của "
                + entry.getKey()
                + " phải bằng 1.0, hiện là "
                + entry.getValue());
      }
    }
  }

  private List<Plo> savePlos(String pvId, ParsedProgramData data, Verification verification) {
    List<Plo> entities = new ArrayList<>();
    for (PloDraft d : data.plos()) {
      entities.add(
          Plo.builder()
              .programVersionId(pvId)
              .code(d.code())
              .displayOrder(d.displayOrder())
              .category(d.category())
              .contentOriginal(d.name().isEmpty() ? null : d.name())
              .contentSummary(d.name().isEmpty() ? d.code() : d.name())
              .capabilityLevelRaw(d.capabilityLevelRaw())
              .passThreshold(d.passThreshold())
              .verification(verification)
              .build());
    }
    return ploRepository.saveAll(entities);
  }

  private List<Pi> savePis(
      String pvId, ParsedProgramData data, Map<String, Plo> ploByCode, Verification verification) {
    List<Pi> entities = new ArrayList<>();
    for (PiDraft d : data.pis()) {
      Plo parentPlo = ploByCode.get(d.ploCode());
      entities.add(
          Pi.builder()
              .programVersionId(pvId)
              .ploId(parentPlo.getId())
              .code(d.code())
              .displayOrder(d.displayOrder())
              .contentOriginal(d.name().isEmpty() ? null : d.name())
              .contentShort(d.name().isEmpty() ? d.code() : d.name())
              .capabilityLevelRaw(d.capabilityLevelRaw())
              .weight(d.weight())
              .passThreshold(d.passThreshold())
              .verification(verification)
              .build());
    }
    return piRepository.saveAll(entities);
  }

  private int saveProgramCourses(String pvId, Set<String> courseCodes, List<String> warnings) {
    List<ProgramCourse> list = new ArrayList<>();
    for (String code : courseCodes) {
      Optional<Course> found = courseRepository.findByCode(code);
      if (found.isEmpty()) {
        warnings.add("Không tìm thấy học phần mã " + code + " trong danh mục; đã bỏ qua.");
        continue;
      }
      list.add(
          ProgramCourse.builder().programVersionId(pvId).courseId(found.get().getId()).build());
    }
    return programCourseRepository.saveAll(list).size();
  }

  private Integer parseCapabilityLevel(String raw, String fieldLabel, int rowNum) {
    if (raw == null || raw.isEmpty()) {
      return null;
    }
    try {
      int level = Integer.parseInt(raw);
      if (level < 1 || level > 6) {
        throw new BusinessRuleViolationException(
            "Dòng " + rowNum + ": " + fieldLabel + " phải nằm trong [1, 6], nhận được " + raw);
      }
      return level;
    } catch (NumberFormatException ex) {
      throw new BusinessRuleViolationException(
          "Dòng " + rowNum + ": giá trị số nguyên không hợp lệ cho " + fieldLabel + ": " + raw);
    }
  }

  private PloCategory parsePloCategory(String raw, int rowNum) {
    if (raw == null || raw.isEmpty()) {
      return null;
    }
    try {
      return PloCategory.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      throw new BusinessRuleViolationException(
          "Dòng "
              + rowNum
              + ": ploCategory không hợp lệ ("
              + raw
              + "), chỉ chấp nhận GENERAL, SPECIALIZED hoặc UNSPECIFIED");
    }
  }

  private record PloDraft(
      String code,
      String name,
      double passThreshold,
      int displayOrder,
      Integer capabilityLevelRaw,
      PloCategory category) {}

  private record PiDraft(
      String ploCode,
      String code,
      String name,
      double weight,
      double passThreshold,
      Integer capabilityLevelRaw,
      int displayOrder) {}

  private record ParsedProgramData(
      List<PloDraft> plos, List<PiDraft> pis, Set<String> courseCodes, List<String> warnings) {}
}
