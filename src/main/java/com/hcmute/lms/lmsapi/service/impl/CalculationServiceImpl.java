package com.hcmute.lms.lmsapi.service.impl;

import com.hcmute.lms.lmsapi.document.AggregateResult;
import com.hcmute.lms.lmsapi.document.Assessment;
import com.hcmute.lms.lmsapi.document.AssessmentCloWeight;
import com.hcmute.lms.lmsapi.document.AssessmentItem;
import com.hcmute.lms.lmsapi.document.AuditEntity;
import com.hcmute.lms.lmsapi.document.CalculationRun;
import com.hcmute.lms.lmsapi.document.Clo;
import com.hcmute.lms.lmsapi.document.CloPiLink;
import com.hcmute.lms.lmsapi.document.CourseSection;
import com.hcmute.lms.lmsapi.document.Enrollment;
import com.hcmute.lms.lmsapi.document.OutcomeMapping;
import com.hcmute.lms.lmsapi.document.Pi;
import com.hcmute.lms.lmsapi.document.Plo;
import com.hcmute.lms.lmsapi.document.Score;
import com.hcmute.lms.lmsapi.document.Student;
import com.hcmute.lms.lmsapi.document.StudentResult;
import com.hcmute.lms.lmsapi.document.Syllabus;
import com.hcmute.lms.lmsapi.document.Term;
import com.hcmute.lms.lmsapi.dto.AggregateResultResponse;
import com.hcmute.lms.lmsapi.dto.CalculationRunRequest;
import com.hcmute.lms.lmsapi.dto.CalculationRunResponse;
import com.hcmute.lms.lmsapi.dto.StudentResultResponse;
import com.hcmute.lms.lmsapi.dto.UserPrincipal;
import com.hcmute.lms.lmsapi.enums.AccessType;
import com.hcmute.lms.lmsapi.enums.AggregateScopeType;
import com.hcmute.lms.lmsapi.enums.AssessmentStatus;
import com.hcmute.lms.lmsapi.enums.AuditAction;
import com.hcmute.lms.lmsapi.enums.CalculationScope;
import com.hcmute.lms.lmsapi.enums.CalculationStatus;
import com.hcmute.lms.lmsapi.enums.EnrollmentStatus;
import com.hcmute.lms.lmsapi.enums.ResultInputSourceType;
import com.hcmute.lms.lmsapi.enums.ResultLevel;
import com.hcmute.lms.lmsapi.enums.ResultStatus;
import com.hcmute.lms.lmsapi.exception.BusinessRuleViolationException;
import com.hcmute.lms.lmsapi.exception.ResourceNotFoundException;
import com.hcmute.lms.lmsapi.repository.AggregateResultRepository;
import com.hcmute.lms.lmsapi.repository.AssessmentRepository;
import com.hcmute.lms.lmsapi.repository.CalculationRunRepository;
import com.hcmute.lms.lmsapi.repository.CloPiLinkRepository;
import com.hcmute.lms.lmsapi.repository.CloRepository;
import com.hcmute.lms.lmsapi.repository.CourseSectionRepository;
import com.hcmute.lms.lmsapi.repository.EnrollmentRepository;
import com.hcmute.lms.lmsapi.repository.PiRepository;
import com.hcmute.lms.lmsapi.repository.PloRepository;
import com.hcmute.lms.lmsapi.repository.ScoreRepository;
import com.hcmute.lms.lmsapi.repository.StudentRepository;
import com.hcmute.lms.lmsapi.repository.StudentResultRepository;
import com.hcmute.lms.lmsapi.repository.SyllabusRepository;
import com.hcmute.lms.lmsapi.repository.TermRepository;
import com.hcmute.lms.lmsapi.service.AuditService;
import com.hcmute.lms.lmsapi.service.CalculationService;
import com.hcmute.lms.lmsapi.service.ScopeAuthorizationService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/** Triển khai engine đo lường chuẩn đầu ra 4 cấp độ (F06: ASSESSMENT_CLO -> CLO -> PI -> PLO). */
@Service
@RequiredArgsConstructor
public class CalculationServiceImpl implements CalculationService {

  private static final double WEIGHT_TOLERANCE = 1e-4;
  private static final double DEFAULT_PASS_THRESHOLD = 0.5;

  private final CalculationRunRepository calculationRunRepository;
  private final StudentResultRepository studentResultRepository;
  private final AggregateResultRepository aggregateResultRepository;
  private final CourseSectionRepository courseSectionRepository;
  private final SyllabusRepository syllabusRepository;
  private final AssessmentRepository assessmentRepository;
  private final EnrollmentRepository enrollmentRepository;
  private final ScoreRepository scoreRepository;
  private final CloRepository cloRepository;
  private final CloPiLinkRepository cloPiLinkRepository;
  private final PiRepository piRepository;
  private final PloRepository ploRepository;
  private final StudentRepository studentRepository;
  private final TermRepository termRepository;
  private final AuditService auditService;
  private final ScopeAuthorizationService scopeAuthorizationService;

  @Override
  public CalculationRunResponse triggerCalculation(CalculationRunRequest request, String actorId) {
    List<CourseSection> sections = resolveSections(request);
    verifyCurrentUserScopeAccess(request, sections);

    String programVersionId = resolveProgramVersionId(request, sections);
    String lockKey = buildLockKey(request, programVersionId, sections);
    if (calculationRunRepository.existsByLockKeyAndStatusIn(
        lockKey, List.of(CalculationStatus.PENDING, CalculationStatus.PROCESSING))) {
      throw new BusinessRuleViolationException(
          "Đang có lượt tính toán chạy cùng phạm vi (lockKey=" + lockKey + ")");
    }

    List<String> syllabusIds =
        sections.stream()
            .map(CourseSection::getSyllabusId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();

    CalculationRun run =
        calculationRunRepository.save(
            CalculationRun.builder()
                .scope(request.getScope())
                .scopeRef(
                    CalculationRun.ScopeRef.builder()
                        .sectionIds(sections.stream().map(CourseSection::getId).toList())
                        .courseIds(safeList(request.getCourseIds()))
                        .cohorts(safeList(request.getCohorts()))
                        .termId(request.getTermId())
                        .build())
                .lockKey(lockKey)
                .programVersionId(programVersionId)
                .syllabusIds(syllabusIds)
                .official(request.isOfficial())
                .stale(false)
                .status(CalculationStatus.PROCESSING)
                .triggeredBy(actorId)
                .startedAt(Instant.now())
                .build());

    try {
      ExecutionAccumulator acc =
          executeFourLevelCalculation(run, request, sections, programVersionId);
      run.setDataSnapshot(acc.dataSnapshot);
      run.setStatus(CalculationStatus.SUCCESS);
      run.setFinishedAt(Instant.now());
      CalculationRun savedRun = calculationRunRepository.save(run);
      auditService.record(
          AuditAction.RUN_CALCULATION,
          new AuditEntity("calculation_runs", savedRun.getId()),
          null,
          Map.of("scope", savedRun.getScope().name(), "status", savedRun.getStatus().name()),
          "Chạy đo lường chuẩn đầu ra 4 cấp độ");
      return toRunResponse(savedRun, acc.studentResults.size(), acc.aggregateResults.size());
    } catch (RuntimeException ex) {
      run.setStatus(CalculationStatus.FAILED);
      run.setErrorMessage(ex.getMessage());
      run.setFinishedAt(Instant.now());
      calculationRunRepository.save(run);
      throw ex;
    }
  }

  @Override
  public List<CalculationRunResponse> getCalculationRuns(String programVersionId) {
    List<CalculationRun> runs =
        programVersionId != null && !programVersionId.isBlank()
            ? calculationRunRepository.findByProgramVersionIdOrderByStartedAtDesc(programVersionId)
            : calculationRunRepository.findAllByOrderByStartedAtDesc();
    return runs.stream()
        .map(
            r ->
                toRunResponse(
                    r,
                    studentResultRepository.findByRunId(r.getId()).size(),
                    aggregateResultRepository.findByRunId(r.getId()).size()))
        .toList();
  }

  @Override
  public CalculationRunResponse getCalculationRun(String runId) {
    CalculationRun run =
        calculationRunRepository
            .findById(runId)
            .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lượt tính: " + runId));
    return toRunResponse(
        run,
        studentResultRepository.findByRunId(runId).size(),
        aggregateResultRepository.findByRunId(runId).size());
  }

  @Override
  public List<StudentResultResponse> getStudentResults(
      String runId, ResultLevel level, String sectionId, String assessmentId) {
    ensureRunExists(runId);
    List<StudentResult> results;
    if (level != null && sectionId != null && assessmentId != null) {
      results =
          studentResultRepository.findByRunIdAndLevelAndSectionIdAndAssessmentId(
              runId, level, sectionId, assessmentId);
    } else if (level != null && sectionId != null) {
      results = studentResultRepository.findByRunIdAndLevelAndSectionId(runId, level, sectionId);
    } else if (level != null) {
      results = studentResultRepository.findByRunIdAndLevel(runId, level);
    } else {
      results = studentResultRepository.findByRunId(runId);
    }

    Map<String, String> studentRefs = loadStudentRefs(results);
    Map<String, String> subjectCodes =
        loadSubjectCodes(results.stream().map(StudentResult::getSubjectId).toList());
    return results.stream()
        .map(
            r ->
                StudentResultResponse.builder()
                    .id(r.getId())
                    .runId(r.getRunId())
                    .level(r.getLevel())
                    .subjectId(r.getSubjectId())
                    .subjectCode(subjectCodes.get(r.getSubjectId()))
                    .sectionId(r.getSectionId())
                    .assessmentId(r.getAssessmentId())
                    .studentId(r.getStudentId())
                    .studentRef(studentRefs.get(r.getStudentId()))
                    .termId(r.getTermId())
                    .status(r.getStatus())
                    .value(r.getValue())
                    .passThreshold(r.getPassThreshold())
                    .passed(r.getPassed())
                    .completeness(r.getCompleteness())
                    .inputs(r.getInputs())
                    .programVersionId(r.getProgramVersionId())
                    .syllabusId(r.getSyllabusId())
                    .build())
        .toList();
  }

  @Override
  public List<AggregateResultResponse> getAggregateResults(
      String runId, ResultLevel level, String sectionId) {
    ensureRunExists(runId);
    List<AggregateResult> results;
    if (level != null) {
      results =
          aggregateResultRepository.findByRunIdAndLevel(runId, level).stream()
              .filter(r -> sectionId == null || sectionId.equals(r.getSectionId()))
              .toList();
    } else if (sectionId != null) {
      results = aggregateResultRepository.findByRunIdAndSectionId(runId, sectionId);
    } else {
      results = aggregateResultRepository.findByRunId(runId);
    }

    Map<String, String> subjectCodes =
        loadSubjectCodes(results.stream().map(AggregateResult::getSubjectId).toList());
    return results.stream()
        .map(r -> toAggregateResponse(r, subjectCodes.get(r.getSubjectId())))
        .toList();
  }

  private ExecutionAccumulator executeFourLevelCalculation(
      CalculationRun run,
      CalculationRunRequest request,
      List<CourseSection> sections,
      String programVersionId) {
    ExecutionAccumulator acc = new ExecutionAccumulator();
    int totalScores = 0;
    Instant lastScoreUpdatedAt = null;

    Map<String, Term> termMap =
        termRepository.findAll().stream()
            .collect(Collectors.toMap(Term::getId, t -> t, (a, b) -> a));

    // Lưu tất cả Level 2 CLO results để phục vụ Level 3 (PI)
    List<StudentResult> allSectionCloResults = new ArrayList<>();
    Set<String> cohortStudentIds = new LinkedHashSet<>();

    for (CourseSection section : sections) {
      Syllabus syllabus =
          syllabusRepository
              .findById(section.getSyllabusId())
              .orElseThrow(
                  () ->
                      new ResourceNotFoundException(
                          "Không tìm thấy đề cương của lớp: " + section.getCode()));
      List<Clo> clos = cloRepository.findBySyllabusIdOrderByCodeAsc(syllabus.getId());
      Map<String, Clo> cloMap = clos.stream().collect(Collectors.toMap(Clo::getId, c -> c));

      List<Enrollment> enrollments =
          filterEnrollmentsByCohort(
              enrollmentRepository.findBySectionIdAndStatus(
                  section.getId(), EnrollmentStatus.ENROLLED),
              request.getCohorts());
      List<String> studentIds = enrollments.stream().map(Enrollment::getStudentId).toList();
      cohortStudentIds.addAll(studentIds);

      List<Assessment> assessments =
          assessmentRepository.findBySectionIdOrderByCodeAsc(section.getId()).stream()
              .filter(
                  a ->
                      a.getStatus() == AssessmentStatus.LOCKED
                          || a.getStatus() == AssessmentStatus.SCORE_ENTERED)
              .toList();

      List<Score> sectionScores = scoreRepository.findBySectionId(section.getId());
      totalScores += sectionScores.size();
      for (Score s : sectionScores) {
        if (s.getUpdatedAt() != null
            && (lastScoreUpdatedAt == null || s.getUpdatedAt().isAfter(lastScoreUpdatedAt))) {
          lastScoreUpdatedAt = s.getUpdatedAt();
        }
      }

      // Cấp độ 1: ASSESSMENT_CLO
      // Map<(assessmentId, cloId), Map<studentId, StudentResult>>
      Map<String, Map<String, StudentResult>> level1ByAssessmentClo =
          computeLevel1AssessmentClo(
              run,
              section,
              syllabus,
              programVersionId,
              cloMap,
              assessments,
              studentIds,
              sectionScores,
              acc);

      // Cấp độ 2: CLO toàn học phần
      List<StudentResult> sectionCloResults =
          computeLevel2CourseClo(
              run,
              section,
              syllabus,
              programVersionId,
              clos,
              assessments,
              studentIds,
              level1ByAssessmentClo,
              acc);
      allSectionCloResults.addAll(sectionCloResults);
    }

    // Cấp độ 3 & 4: PI -> PLO (khi có programVersionId)
    if (programVersionId != null && !programVersionId.isBlank()) {
      List<StudentResult> piResults =
          computeLevel3Pi(
              run, request, programVersionId, cohortStudentIds, allSectionCloResults, termMap, acc);
      computeLevel4Plo(run, request, programVersionId, cohortStudentIds, piResults, acc);
    }

    acc.dataSnapshot =
        CalculationRun.DataSnapshot.builder()
            .totalScores(totalScores)
            .lastScoreUpdatedAt(lastScoreUpdatedAt)
            .scoreChecksum(
                "SCORES:"
                    + totalScores
                    + ":"
                    + (lastScoreUpdatedAt != null ? lastScoreUpdatedAt.toEpochMilli() : 0L))
            .build();
    return acc;
  }

  private Map<String, Map<String, StudentResult>> computeLevel1AssessmentClo(
      CalculationRun run,
      CourseSection section,
      Syllabus syllabus,
      String programVersionId,
      Map<String, Clo> cloMap,
      List<Assessment> assessments,
      List<String> studentIds,
      List<Score> sectionScores,
      ExecutionAccumulator acc) {
    Map<String, Map<String, StudentResult>> level1Map = new HashMap<>();

    for (Assessment assessment : assessments) {
      Map<String, Double> cloMaxTotal = new LinkedHashMap<>();
      Map<String, Integer> cloItemCount = new LinkedHashMap<>();
      for (AssessmentItem item : safeList(assessment.getItems())) {
        for (OutcomeMapping mapping : safeList(item.getOutcomeMappings())) {
          cloMaxTotal.merge(mapping.getCloId(), mapping.getMaxScore(), Double::sum);
          cloItemCount.merge(mapping.getCloId(), 1, Integer::sum);
        }
      }

      for (Map.Entry<String, Double> entry : cloMaxTotal.entrySet()) {
        String cloId = entry.getKey();
        double maxTotal = entry.getValue();
        Clo clo = cloMap.get(cloId);
        if (clo == null || maxTotal <= 0.0) {
          continue;
        }
        double threshold = extractCloThreshold(clo);
        int requiredItems = cloItemCount.getOrDefault(cloId, 0);

        List<StudentResult> batchResults = new ArrayList<>();
        Map<String, StudentResult> byStudent = new HashMap<>();

        for (String studentId : studentIds) {
          List<Score> matchedScores =
              sectionScores.stream()
                  .filter(
                      sc ->
                          assessment.getId().equals(sc.getAssessmentId())
                              && cloId.equals(sc.getCloId())
                              && studentId.equals(sc.getStudentId()))
                  .toList();

          StudentResult sr;
          if (!matchedScores.isEmpty()) {
            double sumScore = matchedScores.stream().mapToDouble(Score::getRawScore).sum();
            double value = clamp01(sumScore / maxTotal);
            List<StudentResult.ResultInput> inputs =
                matchedScores.stream()
                    .map(
                        sc ->
                            StudentResult.ResultInput.builder()
                                .sourceType(ResultInputSourceType.SCORE)
                                .refId(sc.getId())
                                .normalizedValue(
                                    sc.getMaxScore() != null && sc.getMaxScore() > 0
                                        ? clamp01(sc.getRawScore() / sc.getMaxScore())
                                        : 0.0)
                                .weight(sc.getMaxScore())
                                .build())
                    .toList();
            sr =
                StudentResult.builder()
                    .runId(run.getId())
                    .level(ResultLevel.ASSESSMENT_CLO)
                    .subjectId(cloId)
                    .sectionId(section.getId())
                    .assessmentId(assessment.getId())
                    .studentId(studentId)
                    .termId(section.getTermId())
                    .status(ResultStatus.COMPUTED)
                    .value(value)
                    .passThreshold(threshold)
                    .passed(value >= threshold)
                    .completeness(
                        StudentResult.Completeness.builder()
                            .requiredCount(requiredItems)
                            .availableCount(matchedScores.size())
                            .ratio(
                                requiredItems > 0
                                    ? Math.min(1.0, (double) matchedScores.size() / requiredItems)
                                    : 1.0)
                            .build())
                    .inputs(inputs)
                    .programVersionId(programVersionId)
                    .syllabusId(syllabus.getId())
                    .build();
          } else {
            sr =
                insufficientResult(
                    run.getId(),
                    ResultLevel.ASSESSMENT_CLO,
                    cloId,
                    section.getId(),
                    assessment.getId(),
                    studentId,
                    section.getTermId(),
                    threshold,
                    requiredItems,
                    programVersionId,
                    syllabus.getId());
          }
          batchResults.add(sr);
        }

        List<StudentResult> saved = studentResultRepository.saveAll(batchResults);
        acc.studentResults.addAll(saved);
        for (StudentResult sr : saved) {
          byStudent.put(sr.getStudentId(), sr);
        }
        level1Map.put(assessment.getId() + ":" + cloId, byStudent);

        AggregateResult agg =
            buildAggregate(
                run.getId(),
                ResultLevel.ASSESSMENT_CLO,
                cloId,
                section.getId(),
                assessment.getId(),
                AggregateResult.Scope.builder()
                    .type(AggregateScopeType.ASSESSMENT)
                    .refId(assessment.getId())
                    .termId(section.getTermId())
                    .build(),
                studentIds.size(),
                saved,
                threshold,
                programVersionId);
        acc.aggregateResults.add(aggregateResultRepository.save(agg));
      }
    }
    return level1Map;
  }

  private List<StudentResult> computeLevel2CourseClo(
      CalculationRun run,
      CourseSection section,
      Syllabus syllabus,
      String programVersionId,
      List<Clo> clos,
      List<Assessment> assessments,
      List<String> studentIds,
      Map<String, Map<String, StudentResult>> level1ByAssessmentClo,
      ExecutionAccumulator acc) {
    List<StudentResult> allLevel2 = new ArrayList<>();

    for (Clo clo : clos) {
      List<AssessmentWeightPair> contributing = new ArrayList<>();
      for (Assessment assessment : assessments) {
        for (AssessmentCloWeight w : safeList(assessment.getCloWeights())) {
          if (clo.getId().equals(w.getCloId()) && w.getWeight() > 0) {
            contributing.add(new AssessmentWeightPair(assessment.getId(), w.getWeight()));
          }
        }
      }
      if (contributing.isEmpty()) {
        continue;
      }

      double weightSum = contributing.stream().mapToDouble(AssessmentWeightPair::weight).sum();
      if (Math.abs(weightSum - 1.0) > WEIGHT_TOLERANCE) {
        throw new BusinessRuleViolationException(
            "Tổng trọng số các bài đánh giá cho CLO "
                + clo.getCode()
                + " ở lớp "
                + section.getCode()
                + " phải bằng 1.0 (hiện tại: "
                + weightSum
                + ")");
      }

      double threshold = extractCloThreshold(clo);
      List<StudentResult> batchResults = new ArrayList<>();

      for (String studentId : studentIds) {
        boolean allComputed = true;
        double weightedValue = 0.0;
        int availableCount = 0;
        List<StudentResult.ResultInput> inputs = new ArrayList<>();

        for (AssessmentWeightPair pair : contributing) {
          Map<String, StudentResult> byStudent =
              level1ByAssessmentClo.getOrDefault(pair.assessmentId() + ":" + clo.getId(), Map.of());
          StudentResult l1 = byStudent.get(studentId);
          if (l1 != null && l1.getStatus() == ResultStatus.COMPUTED && l1.getValue() != null) {
            weightedValue += pair.weight() * l1.getValue();
            availableCount++;
            inputs.add(
                StudentResult.ResultInput.builder()
                    .sourceType(ResultInputSourceType.STUDENT_RESULT)
                    .refId(l1.getId())
                    .normalizedValue(l1.getValue())
                    .weight(pair.weight())
                    .build());
          } else {
            allComputed = false;
          }
        }

        StudentResult sr;
        if (allComputed) {
          double value = clamp01(weightedValue);
          sr =
              StudentResult.builder()
                  .runId(run.getId())
                  .level(ResultLevel.CLO)
                  .subjectId(clo.getId())
                  .sectionId(section.getId())
                  .assessmentId(null)
                  .studentId(studentId)
                  .termId(section.getTermId())
                  .status(ResultStatus.COMPUTED)
                  .value(value)
                  .passThreshold(threshold)
                  .passed(value >= threshold)
                  .completeness(
                      StudentResult.Completeness.builder()
                          .requiredCount(contributing.size())
                          .availableCount(availableCount)
                          .ratio(1.0)
                          .build())
                  .inputs(inputs)
                  .programVersionId(programVersionId)
                  .syllabusId(syllabus.getId())
                  .build();
        } else {
          sr =
              insufficientResult(
                  run.getId(),
                  ResultLevel.CLO,
                  clo.getId(),
                  section.getId(),
                  null,
                  studentId,
                  section.getTermId(),
                  threshold,
                  contributing.size(),
                  programVersionId,
                  syllabus.getId());
        }
        batchResults.add(sr);
      }

      List<StudentResult> saved = studentResultRepository.saveAll(batchResults);
      acc.studentResults.addAll(saved);
      allLevel2.addAll(saved);

      AggregateResult agg =
          buildAggregate(
              run.getId(),
              ResultLevel.CLO,
              clo.getId(),
              section.getId(),
              null,
              AggregateResult.Scope.builder()
                  .type(AggregateScopeType.SECTION)
                  .refId(section.getId())
                  .termId(section.getTermId())
                  .build(),
              studentIds.size(),
              saved,
              threshold,
              programVersionId);
      acc.aggregateResults.add(aggregateResultRepository.save(agg));
    }
    return allLevel2;
  }

  private List<StudentResult> computeLevel3Pi(
      CalculationRun run,
      CalculationRunRequest request,
      String programVersionId,
      Set<String> cohortStudentIds,
      List<StudentResult> allSectionCloResults,
      Map<String, Term> termMap,
      ExecutionAccumulator acc) {
    List<Pi> pis = piRepository.findByProgramVersionIdOrderByDisplayOrderAsc(programVersionId);
    List<CloPiLink> links = cloPiLinkRepository.findByProgramVersionId(programVersionId);
    if (pis.isEmpty() || links.isEmpty() || cohortStudentIds.isEmpty()) {
      return List.of();
    }

    // Chọn lần học gần nhất (LATEST) cho mỗi cặp (studentId, cloId)
    Map<String, StudentResult> latestCloByStudentAndClo = new HashMap<>();
    for (StudentResult sr : allSectionCloResults) {
      if (sr.getStatus() != ResultStatus.COMPUTED) {
        continue;
      }
      String key = sr.getStudentId() + ":" + sr.getSubjectId();
      StudentResult existing = latestCloByStudentAndClo.get(key);
      if (existing == null || isLaterTerm(sr.getTermId(), existing.getTermId(), termMap)) {
        latestCloByStudentAndClo.put(key, sr);
      }
    }

    Map<String, List<CloPiLink>> linksByPi =
        links.stream().collect(Collectors.groupingBy(CloPiLink::getPiId));
    String cohortLabel = resolveCohortLabel(request);
    AggregateScopeType scopeType =
        request.getScope() == CalculationScope.COHORT
            ? AggregateScopeType.COHORT
            : AggregateScopeType.PROGRAM;

    List<StudentResult> allPiResults = new ArrayList<>();
    for (Pi pi : pis) {
      List<CloPiLink> piLinks = linksByPi.getOrDefault(pi.getId(), List.of());
      if (piLinks.isEmpty()) {
        continue;
      }
      double threshold =
          pi.getPassThreshold() != null ? pi.getPassThreshold() : DEFAULT_PASS_THRESHOLD;
      List<StudentResult> batchResults = new ArrayList<>();

      for (String studentId : cohortStudentIds) {
        double weightedSum = 0.0;
        double weightDenom = 0.0;
        List<StudentResult.ResultInput> inputs = new ArrayList<>();

        for (CloPiLink link : piLinks) {
          StudentResult cloRes = latestCloByStudentAndClo.get(studentId + ":" + link.getCloId());
          if (cloRes != null && cloRes.getValue() != null && link.getWeight() > 0) {
            weightedSum += link.getWeight() * cloRes.getValue();
            weightDenom += link.getWeight();
            inputs.add(
                StudentResult.ResultInput.builder()
                    .sourceType(ResultInputSourceType.STUDENT_RESULT)
                    .refId(cloRes.getId())
                    .normalizedValue(cloRes.getValue())
                    .weight(link.getWeight())
                    .build());
          }
        }

        StudentResult sr;
        if (weightDenom > 0.0) {
          double value = clamp01(weightedSum / weightDenom);
          sr =
              StudentResult.builder()
                  .runId(run.getId())
                  .level(ResultLevel.PI)
                  .subjectId(pi.getId())
                  .sectionId(null)
                  .assessmentId(null)
                  .studentId(studentId)
                  .termId(request.getTermId())
                  .status(ResultStatus.COMPUTED)
                  .value(value)
                  .passThreshold(threshold)
                  .passed(value >= threshold)
                  .completeness(
                      StudentResult.Completeness.builder()
                          .requiredCount(piLinks.size())
                          .availableCount(inputs.size())
                          .ratio((double) inputs.size() / piLinks.size())
                          .build())
                  .inputs(inputs)
                  .programVersionId(programVersionId)
                  .build();
        } else {
          sr =
              insufficientResult(
                  run.getId(),
                  ResultLevel.PI,
                  pi.getId(),
                  null,
                  null,
                  studentId,
                  request.getTermId(),
                  threshold,
                  piLinks.size(),
                  programVersionId,
                  null);
        }
        batchResults.add(sr);
      }

      List<StudentResult> saved = studentResultRepository.saveAll(batchResults);
      acc.studentResults.addAll(saved);
      allPiResults.addAll(saved);

      AggregateResult agg =
          buildAggregate(
              run.getId(),
              ResultLevel.PI,
              pi.getId(),
              null,
              null,
              AggregateResult.Scope.builder()
                  .type(scopeType)
                  .refId(programVersionId)
                  .cohort(cohortLabel)
                  .termId(request.getTermId())
                  .build(),
              cohortStudentIds.size(),
              saved,
              threshold,
              programVersionId);
      acc.aggregateResults.add(aggregateResultRepository.save(agg));
    }
    return allPiResults;
  }

  private void computeLevel4Plo(
      CalculationRun run,
      CalculationRunRequest request,
      String programVersionId,
      Set<String> cohortStudentIds,
      List<StudentResult> piResults,
      ExecutionAccumulator acc) {
    List<Plo> plos = ploRepository.findByProgramVersionIdOrderByDisplayOrderAsc(programVersionId);
    List<Pi> pis = piRepository.findByProgramVersionIdOrderByDisplayOrderAsc(programVersionId);
    if (plos.isEmpty() || pis.isEmpty() || cohortStudentIds.isEmpty()) {
      return;
    }

    Map<String, List<Pi>> pisByPlo = pis.stream().collect(Collectors.groupingBy(Pi::getPloId));
    Map<String, StudentResult> piResByStudentAndPi = new HashMap<>();
    for (StudentResult sr : piResults) {
      if (sr.getStatus() == ResultStatus.COMPUTED) {
        piResByStudentAndPi.put(sr.getStudentId() + ":" + sr.getSubjectId(), sr);
      }
    }

    String cohortLabel = resolveCohortLabel(request);
    AggregateScopeType scopeType =
        request.getScope() == CalculationScope.COHORT
            ? AggregateScopeType.COHORT
            : AggregateScopeType.PROGRAM;

    for (Plo plo : plos) {
      List<Pi> childPis = pisByPlo.getOrDefault(plo.getId(), List.of());
      if (childPis.isEmpty()) {
        continue;
      }
      double weightSum =
          childPis.stream().mapToDouble(p -> p.getWeight() != null ? p.getWeight() : 0.0).sum();
      if (Math.abs(weightSum - 1.0) > WEIGHT_TOLERANCE) {
        throw new BusinessRuleViolationException(
            "Tổng trọng số các PI thuộc PLO "
                + plo.getCode()
                + " phải bằng 1.0 (hiện tại: "
                + weightSum
                + ")");
      }

      double threshold =
          plo.getPassThreshold() != null ? plo.getPassThreshold() : DEFAULT_PASS_THRESHOLD;
      List<StudentResult> batchResults = new ArrayList<>();

      for (String studentId : cohortStudentIds) {
        double weightedSum = 0.0;
        double weightDenom = 0.0;
        List<StudentResult.ResultInput> inputs = new ArrayList<>();

        for (Pi pi : childPis) {
          StudentResult piRes = piResByStudentAndPi.get(studentId + ":" + pi.getId());
          double w = pi.getWeight() != null ? pi.getWeight() : 0.0;
          if (piRes != null && piRes.getValue() != null && w > 0) {
            weightedSum += w * piRes.getValue();
            weightDenom += w;
            inputs.add(
                StudentResult.ResultInput.builder()
                    .sourceType(ResultInputSourceType.STUDENT_RESULT)
                    .refId(piRes.getId())
                    .normalizedValue(piRes.getValue())
                    .weight(w)
                    .build());
          }
        }

        StudentResult sr;
        if (weightDenom > 0.0) {
          double value = clamp01(weightedSum / weightDenom);
          sr =
              StudentResult.builder()
                  .runId(run.getId())
                  .level(ResultLevel.PLO)
                  .subjectId(plo.getId())
                  .sectionId(null)
                  .assessmentId(null)
                  .studentId(studentId)
                  .termId(request.getTermId())
                  .status(ResultStatus.COMPUTED)
                  .value(value)
                  .passThreshold(threshold)
                  .passed(value >= threshold)
                  .completeness(
                      StudentResult.Completeness.builder()
                          .requiredCount(childPis.size())
                          .availableCount(inputs.size())
                          .ratio((double) inputs.size() / childPis.size())
                          .build())
                  .inputs(inputs)
                  .programVersionId(programVersionId)
                  .build();
        } else {
          sr =
              insufficientResult(
                  run.getId(),
                  ResultLevel.PLO,
                  plo.getId(),
                  null,
                  null,
                  studentId,
                  request.getTermId(),
                  threshold,
                  childPis.size(),
                  programVersionId,
                  null);
        }
        batchResults.add(sr);
      }

      List<StudentResult> saved = studentResultRepository.saveAll(batchResults);
      acc.studentResults.addAll(saved);

      AggregateResult agg =
          buildAggregate(
              run.getId(),
              ResultLevel.PLO,
              plo.getId(),
              null,
              null,
              AggregateResult.Scope.builder()
                  .type(scopeType)
                  .refId(programVersionId)
                  .cohort(cohortLabel)
                  .termId(request.getTermId())
                  .build(),
              cohortStudentIds.size(),
              saved,
              threshold,
              programVersionId);
      acc.aggregateResults.add(aggregateResultRepository.save(agg));
    }
  }

  private List<CourseSection> resolveSections(CalculationRunRequest request) {
    if (request.getScope() == CalculationScope.SECTION) {
      List<String> ids = safeList(request.getSectionIds());
      if (ids.isEmpty()) {
        throw new BusinessRuleViolationException(
            "Phạm vi SECTION bắt buộc cung cấp danh sách sectionIds");
      }
      List<CourseSection> sections = courseSectionRepository.findAllById(ids);
      if (sections.size() != ids.size()) {
        throw new ResourceNotFoundException("Một hoặc nhiều lớp học phần không tồn tại");
      }
      return sections;
    }

    List<String> explicitIds = safeList(request.getSectionIds());
    List<CourseSection> candidates =
        !explicitIds.isEmpty()
            ? courseSectionRepository.findAllById(explicitIds)
            : courseSectionRepository.findAll();

    Set<String> courseFilter = new LinkedHashSet<>(safeList(request.getCourseIds()));
    return candidates.stream()
        .filter(s -> request.getTermId() == null || request.getTermId().equals(s.getTermId()))
        .filter(s -> courseFilter.isEmpty() || courseFilter.contains(s.getCourseId()))
        .toList();
  }

  private void verifyCurrentUserScopeAccess(
      CalculationRunRequest request, List<CourseSection> sections) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
      return;
    }
    if (request.getScope() == CalculationScope.SECTION) {
      for (CourseSection section : sections) {
        if (!scopeAuthorizationService.canAccessLoadedSection(
            principal, section, AccessType.READ)) {
          throw new AccessDeniedException(
              "Không có quyền chạy tính toán trên lớp: " + section.getCode());
        }
      }
    } else if (request.getProgramVersionId() != null
        && !scopeAuthorizationService.canAccessProgramVersion(
            principal, request.getProgramVersionId(), AccessType.READ)) {
      throw new AccessDeniedException("Không có quyền truy cập phiên bản CTĐT này");
    }
  }

  private String resolveProgramVersionId(
      CalculationRunRequest request, List<CourseSection> sections) {
    if (request.getProgramVersionId() != null && !request.getProgramVersionId().isBlank()) {
      return request.getProgramVersionId();
    }
    return null;
  }

  private List<Enrollment> filterEnrollmentsByCohort(
      List<Enrollment> enrollments, List<String> cohorts) {
    if (cohorts == null || cohorts.isEmpty()) {
      return enrollments;
    }
    Set<String> cohortSet = new LinkedHashSet<>(cohorts);
    Map<String, Student> students =
        studentRepository
            .findAllById(enrollments.stream().map(Enrollment::getStudentId).toList())
            .stream()
            .collect(Collectors.toMap(Student::getId, s -> s));
    return enrollments.stream()
        .filter(
            e -> {
              Student s = students.get(e.getStudentId());
              return s != null && s.getCohort() != null && cohortSet.contains(s.getCohort());
            })
        .toList();
  }

  private boolean isLaterTerm(
      String candidateTermId, String currentTermId, Map<String, Term> termMap) {
    if (candidateTermId == null) {
      return false;
    }
    if (currentTermId == null) {
      return true;
    }
    Term cand = termMap.get(candidateTermId);
    Term curr = termMap.get(currentTermId);
    if (cand != null
        && curr != null
        && cand.getStartDate() != null
        && curr.getStartDate() != null) {
      return cand.getStartDate().isAfter(curr.getStartDate());
    }
    return candidateTermId.compareTo(currentTermId) > 0;
  }

  private AggregateResult buildAggregate(
      String runId,
      ResultLevel level,
      String subjectId,
      String sectionId,
      String assessmentId,
      AggregateResult.Scope scope,
      int enrolledCount,
      List<StudentResult> studentResults,
      double threshold,
      String programVersionId) {
    List<StudentResult> computed =
        studentResults.stream()
            .filter(r -> r.getStatus() == ResultStatus.COMPUTED && r.getValue() != null)
            .toList();
    int passed = (int) computed.stream().filter(r -> Boolean.TRUE.equals(r.getPassed())).count();
    int failed = computed.size() - passed;
    Double mean =
        computed.isEmpty()
            ? null
            : computed.stream().mapToDouble(StudentResult::getValue).average().orElse(0.0);
    Double passRate = computed.isEmpty() ? null : (double) passed / computed.size();
    double dataCompleteness = enrolledCount > 0 ? (double) computed.size() / enrolledCount : 0.0;

    return AggregateResult.builder()
        .runId(runId)
        .level(level)
        .subjectId(subjectId)
        .sectionId(sectionId)
        .assessmentId(assessmentId)
        .scope(scope)
        .counts(
            AggregateResult.Counts.builder()
                .enrolled(enrolledCount)
                .passed(passed)
                .failed(failed)
                .build())
        .mean(mean)
        .passRate(passRate)
        .passThreshold(threshold)
        .dataCompleteness(dataCompleteness)
        .programVersionId(programVersionId)
        .build();
  }

  private StudentResult insufficientResult(
      String runId,
      ResultLevel level,
      String subjectId,
      String sectionId,
      String assessmentId,
      String studentId,
      String termId,
      double threshold,
      int requiredCount,
      String programVersionId,
      String syllabusId) {
    return StudentResult.builder()
        .runId(runId)
        .level(level)
        .subjectId(subjectId)
        .sectionId(sectionId)
        .assessmentId(assessmentId)
        .studentId(studentId)
        .termId(termId)
        .status(ResultStatus.INSUFFICIENT_DATA)
        .value(null)
        .passThreshold(threshold)
        .passed(null)
        .completeness(
            StudentResult.Completeness.builder()
                .requiredCount(requiredCount)
                .availableCount(0)
                .ratio(0.0)
                .build())
        .inputs(List.of())
        .programVersionId(programVersionId)
        .syllabusId(syllabusId)
        .build();
  }

  private CalculationRunResponse toRunResponse(
      CalculationRun run, long studentResultCount, long aggregateResultCount) {
    return CalculationRunResponse.builder()
        .id(run.getId())
        .scope(run.getScope())
        .scopeRef(run.getScopeRef())
        .lockKey(run.getLockKey())
        .programVersionId(run.getProgramVersionId())
        .syllabusIds(run.getSyllabusIds())
        .official(run.isOfficial())
        .dataSnapshot(run.getDataSnapshot())
        .stale(run.isStale())
        .staleReason(run.getStaleReason())
        .status(run.getStatus())
        .errorMessage(run.getErrorMessage())
        .triggeredBy(run.getTriggeredBy())
        .startedAt(run.getStartedAt())
        .finishedAt(run.getFinishedAt())
        .studentResultCount(studentResultCount)
        .aggregateResultCount(aggregateResultCount)
        .build();
  }

  private AggregateResultResponse toAggregateResponse(AggregateResult r, String subjectCode) {
    int enrolled =
        r.getCounts() != null && r.getCounts().getEnrolled() != null
            ? r.getCounts().getEnrolled()
            : 0;
    int passed =
        r.getCounts() != null && r.getCounts().getPassed() != null ? r.getCounts().getPassed() : 0;
    int failed =
        r.getCounts() != null && r.getCounts().getFailed() != null ? r.getCounts().getFailed() : 0;
    int evaluated = passed + failed;
    int insufficient = Math.max(0, enrolled - evaluated);
    Double passRateOfEnrolled = enrolled > 0 ? (double) passed / enrolled : null;
    return AggregateResultResponse.builder()
        .id(r.getId())
        .runId(r.getRunId())
        .level(r.getLevel())
        .subjectId(r.getSubjectId())
        .subjectCode(subjectCode)
        .sectionId(r.getSectionId())
        .assessmentId(r.getAssessmentId())
        .scope(r.getScope())
        .counts(r.getCounts())
        .evaluated(evaluated)
        .insufficientData(insufficient)
        .mean(r.getMean())
        .passRate(r.getPassRate())
        .passRateOfEnrolled(passRateOfEnrolled)
        .passThreshold(r.getPassThreshold())
        .dataCompleteness(r.getDataCompleteness())
        .programVersionId(r.getProgramVersionId())
        .build();
  }

  private Map<String, String> loadStudentRefs(List<StudentResult> results) {
    List<String> ids = results.stream().map(StudentResult::getStudentId).distinct().toList();
    return studentRepository.findAllById(ids).stream()
        .collect(Collectors.toMap(Student::getId, Student::getStudentRef, (a, b) -> a));
  }

  private Map<String, String> loadSubjectCodes(Collection<String> subjectIds) {
    Set<String> unique = new LinkedHashSet<>(subjectIds);
    Map<String, String> codes = new HashMap<>();
    cloRepository.findAllById(unique).forEach(c -> codes.put(c.getId(), c.getCode()));
    piRepository.findAllById(unique).forEach(p -> codes.put(p.getId(), p.getCode()));
    ploRepository.findAllById(unique).forEach(p -> codes.put(p.getId(), p.getCode()));
    return codes;
  }

  private void ensureRunExists(String runId) {
    if (!calculationRunRepository.existsById(runId)) {
      throw new ResourceNotFoundException("Không tìm thấy lượt tính: " + runId);
    }
  }

  private String buildLockKey(
      CalculationRunRequest request, String programVersionId, List<CourseSection> sections) {
    List<String> secIds =
        sections.stream().map(CourseSection::getId).sorted(Comparator.naturalOrder()).toList();
    List<String> cohorts = safeList(request.getCohorts()).stream().sorted().toList();
    return request.getScope().name()
        + ":"
        + (programVersionId != null ? programVersionId : "NONE")
        + ":"
        + String.join(",", secIds)
        + ":"
        + String.join(",", cohorts)
        + ":"
        + (request.getTermId() != null ? request.getTermId() : "NONE");
  }

  private String resolveCohortLabel(CalculationRunRequest request) {
    List<String> cohorts = safeList(request.getCohorts());
    return cohorts.isEmpty() ? null : String.join(",", cohorts);
  }

  private double extractCloThreshold(Clo clo) {
    if (clo.getMeasurement() != null && clo.getMeasurement().getDefaultPassThreshold() != null) {
      return clo.getMeasurement().getDefaultPassThreshold();
    }
    return DEFAULT_PASS_THRESHOLD;
  }

  private double clamp01(double value) {
    return Math.max(0.0, Math.min(1.0, value));
  }

  private <T> List<T> safeList(List<T> list) {
    return list != null ? list : Collections.emptyList();
  }

  private record AssessmentWeightPair(String assessmentId, double weight) {}

  private static final class ExecutionAccumulator {
    final List<StudentResult> studentResults = new ArrayList<>();
    final List<AggregateResult> aggregateResults = new ArrayList<>();
    CalculationRun.DataSnapshot dataSnapshot;
  }
}
