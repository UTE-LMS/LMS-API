package com.hcmute.lms.lmsapi.service.impl;

import com.hcmute.lms.lmsapi.document.Program;
import com.hcmute.lms.lmsapi.dto.PageResponse;
import com.hcmute.lms.lmsapi.dto.ProgramRequest;
import com.hcmute.lms.lmsapi.dto.ProgramResponse;
import com.hcmute.lms.lmsapi.exception.AppException;
import com.hcmute.lms.lmsapi.exception.BusinessRuleViolationException;
import com.hcmute.lms.lmsapi.exception.ErrorCode;
import com.hcmute.lms.lmsapi.exception.ResourceNotFoundException;
import com.hcmute.lms.lmsapi.mapper.ProgramMapper;
import com.hcmute.lms.lmsapi.repository.FacultyRepository;
import com.hcmute.lms.lmsapi.repository.ProgramRepository;
import com.hcmute.lms.lmsapi.service.ProgramService;
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
public class ProgramServiceImpl implements ProgramService {

  private final ProgramRepository programRepository;
  private final FacultyRepository facultyRepository;
  private final ProgramMapper programMapper;
  private final MongoTemplate mongoTemplate;

  @Override
  public PageResponse<ProgramResponse> getPrograms(int page, int size) {
    return getPrograms(page, size, null, null, null, null);
  }

  @Override
  public PageResponse<ProgramResponse> getPrograms(
      int page, int size, String facultyId, String level, String degree, String search) {
    PageableValidationUtils.validate(page, size);
    Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "code"));

    String cleanFacultyId = trimToNull(facultyId);
    String cleanLevel = trimToNull(level);
    String cleanDegree = trimToNull(degree);
    String cleanSearch = trimToNull(search);

    if (cleanFacultyId == null
        && cleanLevel == null
        && cleanDegree == null
        && cleanSearch == null) {
      Page<Program> programPage = programRepository.findAll(pageable);
      List<ProgramResponse> content = programMapper.toResponseList(programPage.getContent());
      return PageResponse.of(
          content, programPage.getNumber(), programPage.getSize(), programPage.getTotalElements());
    }

    List<Criteria> criteriaList = new ArrayList<>();
    if (cleanFacultyId != null) {
      ObjectIdUtils.requireValid(cleanFacultyId, "facultyId");
      criteriaList.add(Criteria.where("facultyId").is(new ObjectId(cleanFacultyId)));
    }
    if (cleanLevel != null) {
      criteriaList.add(Criteria.where("level").regex("^" + Pattern.quote(cleanLevel) + "$", "i"));
    }
    if (cleanDegree != null) {
      criteriaList.add(Criteria.where("degree").regex("^" + Pattern.quote(cleanDegree) + "$", "i"));
    }
    if (cleanSearch != null) {
      String regex = Pattern.quote(cleanSearch);
      criteriaList.add(
          new Criteria()
              .orOperator(
                  Criteria.where("code").regex(regex, "i"),
                  Criteria.where("name").regex(regex, "i")));
    }

    Query query = new Query(new Criteria().andOperator(criteriaList.toArray(Criteria[]::new)));
    long total = mongoTemplate.count(Query.of(query).limit(-1).skip(-1), Program.class);
    query.with(pageable);
    List<Program> programs = mongoTemplate.find(query, Program.class);
    return PageResponse.of(
        programMapper.toResponseList(programs),
        pageable.getPageNumber(),
        pageable.getPageSize(),
        total);
  }

  @Override
  public ProgramResponse getProgramById(String id) {
    ObjectIdUtils.requireValid(id, "id");
    Program program =
        programRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Program", "id", id));
    return programMapper.toResponse(program);
  }

  @Override
  public ProgramResponse createProgram(ProgramRequest request) {
    String code = request.getCode().trim();
    if (programRepository.existsByCode(code)) {
      throw conflict(code);
    }

    String facultyId = trimToNull(request.getFacultyId());
    if (facultyId != null) {
      ObjectIdUtils.requireValid(facultyId, "facultyId");
      var faculty =
          facultyRepository
              .findById(facultyId)
              .orElseThrow(
                  () ->
                      new BusinessRuleViolationException(
                          "FACULTY_NOT_FOUND", "Khoa không tồn tại: " + facultyId));
      if (!faculty.isActive()) {
        throw new BusinessRuleViolationException(
            "FACULTY_INACTIVE", "Khoa đang bị khóa: " + facultyId);
      }
    }

    Program program =
        Program.builder()
            .facultyId(facultyId)
            .code(code)
            .name(request.getName().trim())
            .level(trimToNull(request.getLevel()))
            .degree(trimToNull(request.getDegree()))
            .build();

    try {
      program = programRepository.save(program);
    } catch (DuplicateKeyException ex) {
      log.warn("Duplicate program code on create: {}", code);
      throw conflict(code);
    }
    return programMapper.toResponse(program);
  }

  @Override
  public ProgramResponse updateProgram(String id, ProgramRequest request) {
    ObjectIdUtils.requireValid(id, "id");
    Program program =
        programRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Program", "id", id));

    String code = request.getCode().trim();
    if (!code.equals(program.getCode()) && programRepository.existsByCodeAndIdNot(code, id)) {
      throw conflict(code);
    }

    if (request.getFacultyId() != null) {
      String facultyId = trimToNull(request.getFacultyId());
      if (facultyId != null) {
        ObjectIdUtils.requireValid(facultyId, "facultyId");
        var faculty =
            facultyRepository
                .findById(facultyId)
                .orElseThrow(
                    () ->
                        new BusinessRuleViolationException(
                            "FACULTY_NOT_FOUND", "Khoa không tồn tại: " + facultyId));
        if (!faculty.isActive()) {
          throw new BusinessRuleViolationException(
              "FACULTY_INACTIVE", "Khoa đang bị khóa: " + facultyId);
        }
      }
      program.setFacultyId(facultyId);
    }

    program.setCode(code);
    program.setName(request.getName().trim());
    program.setLevel(trimToNull(request.getLevel()));
    program.setDegree(trimToNull(request.getDegree()));

    try {
      program = programRepository.save(program);
    } catch (DuplicateKeyException ex) {
      log.warn("Duplicate program code on update: {}", code);
      throw conflict(code);
    }
    return programMapper.toResponse(program);
  }

  private AppException conflict(String code) {
    return new AppException(
        ErrorCode.CONFLICT, HttpStatus.CONFLICT, "Mã chương trình đào tạo đã tồn tại: " + code);
  }

  private String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
