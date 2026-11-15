package com.hcmute.lms.lmsapi.service.impl;

import com.hcmute.lms.lmsapi.document.Faculty;
import com.hcmute.lms.lmsapi.document.Program;
import com.hcmute.lms.lmsapi.document.User;
import com.hcmute.lms.lmsapi.dto.FacultyAssignPmRequest;
import com.hcmute.lms.lmsapi.dto.FacultyRequest;
import com.hcmute.lms.lmsapi.dto.FacultyResponse;
import com.hcmute.lms.lmsapi.dto.FacultyStatusUpdateRequest;
import com.hcmute.lms.lmsapi.dto.FacultyTransferPmRequest;
import com.hcmute.lms.lmsapi.dto.FacultyUpdateRequest;
import com.hcmute.lms.lmsapi.dto.PageResponse;
import com.hcmute.lms.lmsapi.dto.UserResponse;
import com.hcmute.lms.lmsapi.enums.ProgramVersionStatus;
import com.hcmute.lms.lmsapi.enums.Role;
import com.hcmute.lms.lmsapi.enums.UserStatus;
import com.hcmute.lms.lmsapi.exception.AppException;
import com.hcmute.lms.lmsapi.exception.BusinessRuleViolationException;
import com.hcmute.lms.lmsapi.exception.ErrorCode;
import com.hcmute.lms.lmsapi.exception.ResourceNotFoundException;
import com.hcmute.lms.lmsapi.mapper.UserMapper;
import com.hcmute.lms.lmsapi.repository.FacultyRepository;
import com.hcmute.lms.lmsapi.repository.ProgramRepository;
import com.hcmute.lms.lmsapi.repository.ProgramVersionRepository;
import com.hcmute.lms.lmsapi.repository.UserRepository;
import com.hcmute.lms.lmsapi.service.FacultyService;
import com.hcmute.lms.lmsapi.util.ObjectIdUtils;
import com.hcmute.lms.lmsapi.util.PageableValidationUtils;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class FacultyServiceImpl implements FacultyService {

  private final FacultyRepository facultyRepository;
  private final UserRepository userRepository;
  private final ProgramRepository programRepository;
  private final ProgramVersionRepository programVersionRepository;
  private final UserMapper userMapper;

  @Override
  public FacultyResponse createFaculty(FacultyRequest request) {
    String code = request.getCode().trim().toUpperCase();
    String name = request.getName().trim();

    if (facultyRepository.existsByCode(code)) {
      throw conflict("Mã khoa đã tồn tại: " + code);
    }
    if (facultyRepository.existsByName(name)) {
      throw conflict("Tên khoa đã tồn tại: " + name);
    }

    Instant now = Instant.now();
    Faculty faculty =
        Faculty.builder()
            .code(code)
            .name(name)
            .description(request.getDescription() != null ? request.getDescription().trim() : null)
            .isActive(true)
            .createdAt(now)
            .updatedAt(now)
            .build();

    try {
      faculty = facultyRepository.save(faculty);
    } catch (DuplicateKeyException ex) {
      throw conflict("Mã hoặc tên khoa đã tồn tại: " + code);
    }
    return toResponse(faculty);
  }

  @Override
  public PageResponse<FacultyResponse> getFaculties(
      int page, int size, String search, Boolean isActive) {
    PageableValidationUtils.validate(page, size);
    Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "code"));
    String keyword = search != null ? search.trim() : "";

    Page<Faculty> facultyPage;
    if (isActive != null && !keyword.isEmpty()) {
      facultyPage =
          facultyRepository
              .findByIsActiveAndCodeContainingIgnoreCaseOrIsActiveAndNameContainingIgnoreCase(
                  isActive, keyword, isActive, keyword, pageable);
    } else if (isActive != null) {
      facultyPage = facultyRepository.findByIsActive(isActive, pageable);
    } else if (!keyword.isEmpty()) {
      facultyPage =
          facultyRepository.findByCodeContainingIgnoreCaseOrNameContainingIgnoreCase(
              keyword, keyword, pageable);
    } else {
      facultyPage = facultyRepository.findAll(pageable);
    }

    List<FacultyResponse> content = facultyPage.getContent().stream().map(this::toResponse).toList();
    return PageResponse.of(
        content, facultyPage.getNumber(), facultyPage.getSize(), facultyPage.getTotalElements());
  }

  @Override
  public FacultyResponse getFacultyById(String id) {
    return toResponse(requireFaculty(id));
  }

  @Override
  public FacultyResponse updateFaculty(String id, FacultyUpdateRequest request) {
    Faculty faculty = requireFaculty(id);
    String name = request.getName().trim();
    if (!name.equals(faculty.getName()) && facultyRepository.existsByNameAndIdNot(name, id)) {
      throw conflict("Tên khoa đã tồn tại: " + name);
    }

    if (Boolean.FALSE.equals(request.getIsActive()) && faculty.isActive()) {
      ensureNoActiveProgramsInFaculty(id);
    }

    faculty.setName(name);
    faculty.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);
    if (request.getIsActive() != null) {
      faculty.setActive(request.getIsActive());
    }
    faculty.setUpdatedAt(Instant.now());

    try {
      faculty = facultyRepository.save(faculty);
    } catch (DuplicateKeyException ex) {
      throw conflict("Tên khoa đã tồn tại: " + name);
    }
    return toResponse(faculty);
  }

  @Override
  public FacultyResponse updateFacultyStatus(String id, FacultyStatusUpdateRequest request) {
    Faculty faculty = requireFaculty(id);
    boolean targetActive = Boolean.TRUE.equals(request.getIsActive());
    if (!targetActive && faculty.isActive()) {
      ensureNoActiveProgramsInFaculty(id);
    }

    faculty.setActive(targetActive);
    faculty.setUpdatedAt(Instant.now());
    faculty = facultyRepository.save(faculty);
    log.info("Cập nhật trạng thái Khoa [{}] isActive=[{}]", id, targetActive);
    return toResponse(faculty);
  }

  @Override
  public List<UserResponse> getProgramManagers(String facultyId) {
    requireFaculty(facultyId);
    List<User> pms =
        userRepository.findByFacultyIdAndRoleAssignmentsRole(facultyId, Role.PROGRAM_MANAGER);
    return userMapper.toResponseList(pms);
  }

  @Override
  public UserResponse assignProgramManager(String facultyId, FacultyAssignPmRequest request) {
    Faculty faculty = requireFaculty(facultyId);
    if (!faculty.isActive()) {
      throw new BusinessRuleViolationException(
          "FACULTY_INACTIVE", "Không thể gán Program Manager vào Khoa đang bị khóa: " + facultyId);
    }

    String userId = request.getUserId().trim();
    User user = requireActiveProgramManager(userId);

    if (user.getFacultyId() != null && !user.getFacultyId().equals(facultyId)) {
      throw conflict(
          "Người dùng ["
              + user.getUsername()
              + "] hiện đang thuộc Khoa khác. Mỗi Program Manager chỉ được thuộc đúng 1 Khoa.");
    }

    user.setFacultyId(facultyId);
    user.setUpdatedAt(Instant.now());
    user = userRepository.save(user);
    log.info("Gán Program Manager [{}] vào Khoa [{}]", userId, facultyId);
    return userMapper.toResponse(user);
  }

  @Override
  public void unassignProgramManager(String facultyId, String userId) {
    requireFaculty(facultyId);
    ObjectIdUtils.requireValid(userId, "userId");
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng: " + userId));

    if (!Objects.equals(user.getFacultyId(), facultyId)) {
      throw new BusinessRuleViolationException(
          "PM_NOT_IN_FACULTY",
          "Program Manager [" + user.getUsername() + "] không thuộc Khoa [" + facultyId + "]");
    }

    user.setFacultyId(null);
    user.setUpdatedAt(Instant.now());
    userRepository.save(user);
    // Thu hồi tức thì toàn bộ JWT access token đang hoạt động của PM
    userRepository.incrementTokenVersion(userId);
    log.info("Hủy gán Program Manager [{}] khỏi Khoa [{}] và thu hồi token", userId, facultyId);
  }

  @Override
  public UserResponse transferProgramManager(FacultyTransferPmRequest request) {
    String userId = request.getUserId().trim();
    String targetFacultyId = request.getTargetFacultyId().trim();

    Faculty targetFaculty = requireFaculty(targetFacultyId);
    if (!targetFaculty.isActive()) {
      throw new BusinessRuleViolationException(
          "FACULTY_INACTIVE",
          "Không thể điều chuyển Program Manager sang Khoa đang bị khóa: " + targetFacultyId);
    }

    User user = requireActiveProgramManager(userId);
    if (Objects.equals(user.getFacultyId(), targetFacultyId)) {
      throw new BusinessRuleViolationException(
          "PM_ALREADY_IN_TARGET_FACULTY",
          "Program Manager [" + user.getUsername() + "] đã thuộc Khoa đích.");
    }

    user.setFacultyId(targetFacultyId);
    user.setUpdatedAt(Instant.now());
    user = userRepository.save(user);
    // Thu hồi tức thì toàn bộ JWT access token cũ mang quyền Khoa cũ
    userRepository.incrementTokenVersion(userId);
    log.info(
        "Điều chuyển Program Manager [{}] sang Khoa [{}] (lý do: {})",
        userId,
        targetFacultyId,
        request.getTransferReason());
    return userMapper.toResponse(user);
  }

  private Faculty requireFaculty(String id) {
    ObjectIdUtils.requireValid(id, "facultyId");
    return facultyRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy Khoa: " + id));
  }

  private User requireActiveProgramManager(String userId) {
    ObjectIdUtils.requireValid(userId, "userId");
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng: " + userId));

    if (user.getStatus() != UserStatus.ACTIVE) {
      throw new BusinessRuleViolationException(
          "USER_NOT_ACTIVE", "Tài khoản người dùng chưa kích hoạt hoặc đang bị khóa: " + userId);
    }

    boolean hasPmRole =
        user.getRoleAssignments() != null
            && user.getRoleAssignments().stream()
                .anyMatch(ra -> ra != null && ra.getRole() == Role.PROGRAM_MANAGER);
    if (!hasPmRole) {
      throw new BusinessRuleViolationException(
          "USER_NOT_PROGRAM_MANAGER",
          "Người dùng [" + user.getUsername() + "] không có vai trò PROGRAM_MANAGER");
    }
    return user;
  }

  private void ensureNoActiveProgramsInFaculty(String facultyId) {
    List<String> programIds =
        programRepository.findByFacultyId(facultyId).stream().map(Program::getId).toList();
    if (!programIds.isEmpty()
        && programVersionRepository.existsByProgramIdInAndStatus(
            programIds, ProgramVersionStatus.ACTIVE)) {
      throw new BusinessRuleViolationException(
          "FACULTY_HAS_ACTIVE_PROGRAMS",
          "Không thể khóa Khoa khi vẫn còn phiên bản CTĐT đang ở trạng thái ACTIVE.");
    }
  }

  private FacultyResponse toResponse(Faculty faculty) {
    long pmCount =
        userRepository.countByFacultyIdAndRoleAssignmentsRole(faculty.getId(), Role.PROGRAM_MANAGER);
    return FacultyResponse.builder()
        .id(faculty.getId())
        .code(faculty.getCode())
        .name(faculty.getName())
        .description(faculty.getDescription())
        .isActive(faculty.isActive())
        .programManagerCount(pmCount)
        .createdAt(faculty.getCreatedAt())
        .updatedAt(faculty.getUpdatedAt())
        .build();
  }

  private AppException conflict(String message) {
    return new AppException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, message);
  }
}
