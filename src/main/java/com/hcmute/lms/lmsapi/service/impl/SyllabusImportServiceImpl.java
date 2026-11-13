package com.hcmute.lms.lmsapi.service.impl;

import com.hcmute.lms.lmsapi.document.AuditEntity;
import com.hcmute.lms.lmsapi.document.Clo;
import com.hcmute.lms.lmsapi.document.CloMeasurement;
import com.hcmute.lms.lmsapi.document.CloPiLink;
import com.hcmute.lms.lmsapi.document.Pi;
import com.hcmute.lms.lmsapi.document.Syllabus;
import com.hcmute.lms.lmsapi.document.Verification;
import com.hcmute.lms.lmsapi.dto.AssessmentScoreTemplate;
import com.hcmute.lms.lmsapi.dto.SyllabusCsvImportResponse;
import com.hcmute.lms.lmsapi.enums.AuditAction;
import com.hcmute.lms.lmsapi.enums.SyllabusStatus;
import com.hcmute.lms.lmsapi.enums.VerificationStatus;
import com.hcmute.lms.lmsapi.exception.BusinessRuleViolationException;
import com.hcmute.lms.lmsapi.exception.ResourceNotFoundException;
import com.hcmute.lms.lmsapi.mapper.CloMapper;
import com.hcmute.lms.lmsapi.mapper.CloPiLinkMapper;
import com.hcmute.lms.lmsapi.repository.CloPiLinkRepository;
import com.hcmute.lms.lmsapi.repository.CloRepository;
import com.hcmute.lms.lmsapi.repository.CourseRepository;
import com.hcmute.lms.lmsapi.repository.PiRepository;
import com.hcmute.lms.lmsapi.repository.ProgramVersionRepository;
import com.hcmute.lms.lmsapi.repository.SyllabusRepository;
import com.hcmute.lms.lmsapi.service.AuditService;
import com.hcmute.lms.lmsapi.service.SyllabusImportService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class SyllabusImportServiceImpl implements SyllabusImportService {

  private final CourseRepository courseRepository;
  private final SyllabusRepository syllabusRepository;
  private final CloRepository cloRepository;
  private final CloPiLinkRepository cloPiLinkRepository;
  private final ProgramVersionRepository programVersionRepository;
  private final PiRepository piRepository;
  private final CloMapper cloMapper;
  private final CloPiLinkMapper cloPiLinkMapper;
  private final AuditService auditService;

  @Override
  public AssessmentScoreTemplate generateTemplateCsv() {
    String csv =
        "\uFEFF"
            + "cloCode,cloDescription,bloomLevel,defaultPassThreshold,piCode,linkWeight\r\n"
            + "CLO1,Hiểu và vận dụng lập trình hướng đối tượng,2,0.6,PI1.2,0.5\r\n"
            + "CLO2,Áp dụng thiết kế lớp và kế thừa,3,0.6,PI6.1,0.5\r\n"
            + "CLO3,Phân tích bài toán thực tế,4,0.6,PI6.2,0.4\r\n"
            + "CLO3,Phân tích bài toán thực tế,4,0.6,PI7.1,0.6\r\n";
    return new AssessmentScoreTemplate(
        "syllabus-clo-template.csv", csv.getBytes(StandardCharsets.UTF_8));
  }

  @Override
  @Transactional
  public SyllabusCsvImportResponse importSyllabusCsv(
      String courseId,
      String version,
      String programVersionId,
      String effectiveFromTermId,
      MultipartFile file,
      String actorId) {
    if (!courseRepository.existsById(courseId)) {
      throw new ResourceNotFoundException("Không tìm thấy học phần với id: " + courseId);
    }
    String trimmedVersion = version == null ? "" : version.trim();
    if (trimmedVersion.isEmpty()) {
      throw new BusinessRuleViolationException("Mã phiên bản đề cương không được để trống");
    }

    Map<String, Pi> piByCode = resolveProgramPis(programVersionId);
    CsvTextParser.ParsedCsv parsed = CsvTextParser.read(file);
    CsvTextParser.requireColumns(
        parsed.headerIndex(), "cloCode", "bloomLevel", "defaultPassThreshold");

    ParsedSyllabusData data = parseSyllabusRows(parsed, piByCode, programVersionId);
    Syllabus syllabus = resolveOrResetDraftSyllabus(courseId, trimmedVersion, effectiveFromTermId);

    Verification verified = Verification.builder().status(VerificationStatus.VERIFIED).build();

    List<Clo> savedClos = saveClos(syllabus.getId(), courseId, data.clos(), verified);
    Map<String, Clo> cloByCode = new LinkedHashMap<>();
    for (Clo clo : savedClos) {
      cloByCode.put(clo.getCode(), clo);
    }

    List<CloPiLink> savedLinks =
        saveLinks(syllabus.getId(), programVersionId, data.links(), cloByCode, piByCode, verified);

    return SyllabusCsvImportResponse.builder()
        .syllabusId(syllabus.getId())
        .courseId(courseId)
        .version(syllabus.getVersion())
        .status(syllabus.getStatus())
        .verificationStatus(VerificationStatus.VERIFIED)
        .cloCount(savedClos.size())
        .linkCount(savedLinks.size())
        .warnings(data.warnings())
        .clos(cloMapper.toResponseList(savedClos))
        .links(cloPiLinkMapper.toResponseList(savedLinks))
        .build();
  }

  @Override
  @Transactional
  public SyllabusCsvImportResponse confirmImport(String syllabusId, String actorId) {
    Syllabus syllabus =
        syllabusRepository
            .findById(syllabusId)
            .orElseThrow(
                () -> new ResourceNotFoundException("Không tìm thấy đề cương: " + syllabusId));

    List<Syllabus> confirmedList =
        syllabusRepository.findByCourseIdAndStatus(
            syllabus.getCourseId(), SyllabusStatus.CONFIRMED);
    for (Syllabus other : confirmedList) {
      if (!other.getId().equals(syllabus.getId())) {
        other.setStatus(SyllabusStatus.ARCHIVED);
        syllabusRepository.save(other);
      }
    }

    Verification verified =
        Verification.builder()
            .status(VerificationStatus.VERIFIED)
            .verifiedBy(actorId)
            .verifiedAt(Instant.now())
            .note("Xác nhận từ luồng Import CSV Đề cương")
            .build();

    syllabus.setStatus(SyllabusStatus.CONFIRMED);
    syllabus.setVerification(verified);
    Syllabus saved = syllabusRepository.save(syllabus);

    List<Clo> clos = cloRepository.findBySyllabusIdOrderByCodeAsc(syllabusId);
    for (Clo clo : clos) {
      clo.setVerification(verified);
    }
    cloRepository.saveAll(clos);

    List<CloPiLink> links = cloPiLinkRepository.findBySyllabusId(syllabusId);
    for (CloPiLink link : links) {
      link.setVerification(verified);
    }
    cloPiLinkRepository.saveAll(links);

    auditService.record(
        AuditAction.IMPORT_CONFIRM,
        AuditEntity.builder().collection("syllabi").id(saved.getId()).build(),
        null,
        saved,
        "Xác nhận import đề cương " + saved.getVersion(),
        actorId,
        null);

    return SyllabusCsvImportResponse.builder()
        .syllabusId(saved.getId())
        .courseId(saved.getCourseId())
        .version(saved.getVersion())
        .status(saved.getStatus())
        .verificationStatus(VerificationStatus.VERIFIED)
        .cloCount(clos.size())
        .linkCount(links.size())
        .warnings(List.of())
        .clos(cloMapper.toResponseList(clos))
        .links(cloPiLinkMapper.toResponseList(links))
        .build();
  }

  private Map<String, Pi> resolveProgramPis(String programVersionId) {
    Map<String, Pi> map = new LinkedHashMap<>();
    if (programVersionId == null || programVersionId.isBlank()) {
      return map;
    }
    if (!programVersionRepository.existsById(programVersionId)) {
      throw new ResourceNotFoundException("Không tìm thấy phiên bản CTĐT: " + programVersionId);
    }
    for (Pi pi : piRepository.findByProgramVersionIdOrderByDisplayOrderAsc(programVersionId)) {
      map.put(pi.getCode(), pi);
    }
    return map;
  }

  private Syllabus resolveOrResetDraftSyllabus(
      String courseId, String version, String effectiveFromTermId) {
    Optional<Syllabus> existing = syllabusRepository.findByCourseIdAndVersion(courseId, version);
    if (existing.isPresent()) {
      Syllabus syl = existing.get();
      if (syl.getStatus() != SyllabusStatus.DRAFT) {
        throw new BusinessRuleViolationException(
            "Đề cương " + version + " đã được xác nhận/lưu trữ, hãy nhập với mã phiên bản mới");
      }
      cloPiLinkRepository.deleteBySyllabusId(syl.getId());
      cloRepository.deleteBySyllabusId(syl.getId());
      if (effectiveFromTermId != null && !effectiveFromTermId.isBlank()) {
        syl.setEffectiveFromTermId(effectiveFromTermId.trim());
      }
      syl.setVerification(Verification.builder().status(VerificationStatus.VERIFIED).build());
      return syllabusRepository.save(syl);
    }
    return syllabusRepository.save(
        Syllabus.builder()
            .courseId(courseId)
            .version(version)
            .effectiveFromTermId(
                effectiveFromTermId != null && !effectiveFromTermId.isBlank()
                    ? effectiveFromTermId.trim()
                    : null)
            .status(SyllabusStatus.DRAFT)
            .verification(Verification.builder().status(VerificationStatus.VERIFIED).build())
            .build());
  }

  private ParsedSyllabusData parseSyllabusRows(
      CsvTextParser.ParsedCsv parsed, Map<String, Pi> piByCode, String programVersionId) {
    Map<String, CloDraft> clos = new LinkedHashMap<>();
    List<LinkDraft> links = new ArrayList<>();
    List<String> warnings = new ArrayList<>();

    for (int i = 0; i < parsed.rows().size(); i++) {
      int rowNum = i + 2;
      List<String> row = parsed.rows().get(i);
      String cloCode = CsvTextParser.cell(parsed.headerIndex(), row, "cloCode");
      if (cloCode.isEmpty()) {
        throw new BusinessRuleViolationException("Dòng " + rowNum + ": thiếu cloCode");
      }
      int bloomLevel =
          CsvTextParser.parseIntOrDefault(
              CsvTextParser.cell(parsed.headerIndex(), row, "bloomLevel"), 0);
      if (bloomLevel < 1 || bloomLevel > 6) {
        throw new BusinessRuleViolationException(
            "Dòng " + rowNum + ": bloomLevel phải nằm trong [1, 6]");
      }
      double threshold =
          CsvTextParser.parseUnitDouble(
              CsvTextParser.cell(parsed.headerIndex(), row, "defaultPassThreshold"),
              "defaultPassThreshold",
              rowNum);
      String desc = CsvTextParser.cell(parsed.headerIndex(), row, "cloDescription");
      clos.putIfAbsent(cloCode, new CloDraft(cloCode, desc, bloomLevel, threshold));

      String piCode = CsvTextParser.cell(parsed.headerIndex(), row, "piCode");
      if (!piCode.isEmpty()) {
        if (programVersionId == null || programVersionId.isBlank()) {
          throw new BusinessRuleViolationException(
              "Cần chọn phiên bản CTĐT để ánh xạ tới mã PI: " + piCode);
        }
        if (!piByCode.containsKey(piCode)) {
          throw new BusinessRuleViolationException(
              "Dòng "
                  + rowNum
                  + ": không tìm thấy PI '"
                  + piCode
                  + "' trong phiên bản CTĐT đã chọn");
        }
        double weight =
            CsvTextParser.parseUnitDouble(
                CsvTextParser.cell(parsed.headerIndex(), row, "linkWeight"), "linkWeight", rowNum);
        links.add(new LinkDraft(cloCode, piCode, weight));
      } else {
        warnings.add(cloCode + " chưa liên kết PI nào.");
      }
    }
    return new ParsedSyllabusData(new ArrayList<>(clos.values()), links, warnings);
  }

  private List<Clo> saveClos(
      String syllabusId, String courseId, List<CloDraft> drafts, Verification verification) {
    List<Clo> entities = new ArrayList<>();
    for (CloDraft d : drafts) {
      entities.add(
          Clo.builder()
              .syllabusId(syllabusId)
              .courseId(courseId)
              .code(d.code())
              .contentOriginal(d.description().isEmpty() ? null : d.description())
              .bloomLevel(d.bloomLevel())
              .measurement(CloMeasurement.builder().defaultPassThreshold(d.threshold()).build())
              .verification(verification)
              .build());
    }
    return cloRepository.saveAll(entities);
  }

  private List<CloPiLink> saveLinks(
      String syllabusId,
      String programVersionId,
      List<LinkDraft> drafts,
      Map<String, Clo> cloByCode,
      Map<String, Pi> piByCode,
      Verification verification) {
    if (drafts.isEmpty()) {
      return List.of();
    }
    List<CloPiLink> entities = new ArrayList<>();
    for (LinkDraft d : drafts) {
      Clo clo = cloByCode.get(d.cloCode());
      Pi pi = piByCode.get(d.piCode());
      entities.add(
          CloPiLink.builder()
              .programVersionId(programVersionId)
              .syllabusId(syllabusId)
              .cloId(clo.getId())
              .piId(pi.getId())
              .weight(d.weight())
              .verification(verification)
              .build());
    }
    return cloPiLinkRepository.saveAll(entities);
  }

  private record CloDraft(String code, String description, int bloomLevel, double threshold) {}

  private record LinkDraft(String cloCode, String piCode, double weight) {}

  private record ParsedSyllabusData(
      List<CloDraft> clos, List<LinkDraft> links, List<String> warnings) {}
}
