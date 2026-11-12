package com.hcmute.lms.lmsapi.service;

import com.hcmute.lms.lmsapi.document.CourseSection;
import com.hcmute.lms.lmsapi.dto.UserPrincipal;
import com.hcmute.lms.lmsapi.enums.AccessType;

/**
 * Service kiểm tra phân quyền truy cập tài nguyên theo vai trò và phạm vi (F01 / ADR 0003, ADR
 * 0004, ADR 0005, ADR 0010). Áp dụng ma trận phân quyền vai trò × phạm vi × READ/WRITE, quy tắc
 * lecturerIds độc lập, định nghĩa scope rỗng nghiêm ngặt và nguyên lý fail-closed.
 */
public interface ScopeAuthorizationService {

  /**
   * Kiểm tra người dùng có quyền truy cập (READ hoặc WRITE) trên lớp học phần tương ứng hay không.
   *
   * @param principal thông tin người dùng xác thực trong Security Context
   * @param sectionId định danh lớp học phần (MongoDB ObjectId dạng chuỗi)
   * @param accessType loại hành vi truy cập (READ hoặc WRITE)
   * @return {@code true} nếu người dùng được cấp quyền truy cập; {@code false} nếu bị từ chối
   *     (fail-closed)
   */
  boolean canAccessSection(UserPrincipal principal, String sectionId, AccessType accessType);

  /**
   * Như {@link #canAccessSection(UserPrincipal, String, AccessType)} nhưng nhận lớp học phần đã nạp
   * sẵn, để lọc danh sách lớp mà không truy vấn lại từng lớp.
   */
  boolean canAccessLoadedSection(
      UserPrincipal principal, CourseSection section, AccessType accessType);

  /**
   * Quyền mở/sửa/đổi trạng thái lớp học phần của một học phần (US-19, F05a). ADMIN luôn được phép.
   * PROGRAM_MANAGER chỉ được phép khi học phần thuộc ít nhất một phiên bản CTĐT của chương trình
   * nằm trong {@code scope.programIds} (ADR 0005 §3.2: không cho ghi ngoài phạm vi CTĐT phụ trách).
   * Học phần chưa thuộc CTĐT nào thì chỉ ADMIN được phép. Các vai trò khác bị từ chối.
   */
  boolean canManageCourseSections(UserPrincipal principal, String courseId);

  /** Như {@link #canManageCourseSections} nhưng suy {@code courseId} từ lớp học phần. */
  boolean canManageSection(UserPrincipal principal, String sectionId);

  /**
   * Kiểm tra quyền trên một ghi danh thông qua lớp học phần của nó ({@code enrollments.sectionId}).
   */
  boolean canAccessEnrollment(UserPrincipal principal, String enrollmentId, AccessType accessType);

  /**
   * Kiểm tra quyền trên bài đánh giá (F04): bản lớp theo quyền trên lớp ({@link
   * #canAccessSection}), khung theo đề cương theo quyền trên đề cương ({@link #canAccessSyllabus}).
   */
  boolean canAccessAssessment(UserPrincipal principal, String assessmentId, AccessType accessType);

  /**
   * Kiểm tra quyền trên lô nhập điểm (F05b) thông qua lớp học phần của lô ({@code
   * import_batches.sectionId}). Lô không tồn tại thì từ chối (fail-closed).
   */
  boolean canAccessImportBatch(UserPrincipal principal, String batchId, AccessType accessType);

  boolean canAccessProgram(UserPrincipal principal, String programId, AccessType accessType);

  boolean canAccessProgramVersion(
      UserPrincipal principal, String programVersionId, AccessType accessType);

  /**
   * Kiểm tra quyền trên một học phần (ADR 0010). ADMIN luôn được phép. Cho thao tác WRITE, chỉ
   * DEPARTMENT_HEAD hoặc LECTURER có {@code courseId} trong {@code scope.courseIds} được phép; các
   * vai trò khác (PROGRAM_MANAGER, QA_OFFICER,...) bị từ chối (fail-closed).
   */
  boolean canAccessCourse(UserPrincipal principal, String courseId, AccessType accessType);

  /** Kiểm tra quyền trên đề cương thông qua {@code syllabi.courseId} (ADR 0010). */
  boolean canAccessSyllabus(UserPrincipal principal, String syllabusId, AccessType accessType);

  /** Kiểm tra quyền trên CLO thông qua {@code clos.courseId} (ADR 0010). */
  boolean canAccessClo(UserPrincipal principal, String cloId, AccessType accessType);

  /**
   * Kiểm tra quyền trên liên kết CLO–PI thông qua {@code clo_pi_links.programVersionId} (ADR 0010).
   */
  boolean canAccessCloPiLink(UserPrincipal principal, String linkId, AccessType accessType);

  /**
   * Kiểm tra quyền phân công môn học cho giảng viên (ADR 0010). ADMIN luôn được phép.
   * DEPARTMENT_HEAD chỉ được phân công khi {@code courseId} nằm trong {@code scope.courseIds} của
   * chính mình.
   */
  boolean canAssignCourse(UserPrincipal principal, String courseId);
}
