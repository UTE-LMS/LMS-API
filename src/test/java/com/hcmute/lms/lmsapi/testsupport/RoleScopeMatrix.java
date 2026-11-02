package com.hcmute.lms.lmsapi.testsupport;

import com.hcmute.lms.lmsapi.document.UserScope;
import com.hcmute.lms.lmsapi.enums.AccessType;
import com.hcmute.lms.lmsapi.enums.Role;
import java.util.List;
import java.util.stream.Stream;

/**
 * Mẫu tham số hóa cho ma trận vai trò × phạm vi × READ/WRITE (US-03/AC-3, mở rộng cho feature sau).
 *
 * <p>Feature mới chỉ cần: (1) khai báo các {@link Row} lấy từ bảng quyền trong tài liệu kế hoạch
 * (không lấy từ mã), (2) gọi {@link #cases(Row...)} trong {@code @MethodSource}, (3) với mỗi {@link
 * Case} dựng người dùng bằng {@link Case#scope(Targets)} và gọi endpoint tương ứng với {@link
 * Case#access()}, rồi so sánh với {@link Case#allowed()}.
 */
public final class RoleScopeMatrix {

  private RoleScopeMatrix() {}

  /** Các mã tài nguyên mà phạm vi được dựng quanh. */
  public record Targets(String programId, String courseId, String sectionId) {}

  /** Cách xây {@link UserScope} của người dùng trong một dòng ma trận. */
  public enum ScopeKind {
    /** {@code scope == null}. */
    NULL_SCOPE,
    /** Cả ba mảng rỗng (cùng nghĩa scope rỗng với {@link #NULL_SCOPE}, ADR 0003). */
    EMPTY_ARRAYS,
    /** {@code sectionIds} chứa lớp đích. */
    SECTION_MATCH,
    /** {@code courseIds} chứa học phần của lớp đích. */
    COURSE_MATCH,
    /** Chỉ có {@code programIds} chứa CTĐT đích (không suy ra lớp, hạn chế đã biết của F01). */
    PROGRAM_MATCH,
    /** Mọi mã trong phạm vi đều thuộc tài nguyên khác. */
    OTHER_RESOURCES,
    /** {@code scope == null} nhưng người dùng nằm trong {@code lecturerIds} của lớp đích. */
    LISTED_AS_SECTION_LECTURER;

    /** Dựng phạm vi cho các mã đích (trả {@code null} với {@link #NULL_SCOPE}). */
    public UserScope build(Targets targets) {
      return switch (this) {
        case NULL_SCOPE, LISTED_AS_SECTION_LECTURER -> null;
        case EMPTY_ARRAYS -> new UserScope();
        case SECTION_MATCH -> UserScope.builder().sectionIds(List.of(targets.sectionId())).build();
        case COURSE_MATCH -> UserScope.builder().courseIds(List.of(targets.courseId())).build();
        case PROGRAM_MATCH -> UserScope.builder().programIds(List.of(targets.programId())).build();
        case OTHER_RESOURCES ->
            UserScope.builder()
                .programIds(List.of(ApiTestFixtures.missingId()))
                .courseIds(List.of(ApiTestFixtures.missingId()))
                .sectionIds(List.of(ApiTestFixtures.missingId()))
                .build();
      };
    }
  }

  /** Một dòng của bảng quyền: vai trò + phạm vi → có được READ / WRITE hay không. */
  public record Row(Role role, ScopeKind scope, boolean read, boolean write) {}

  /** Một ca kiểm thử cụ thể (một dòng × một hành vi). */
  public record Case(Role role, ScopeKind scopeKind, AccessType access, boolean allowed) {

    /** Phạm vi của người dùng trong ca này. */
    public UserScope scope(Targets targets) {
      return scopeKind.build(targets);
    }

    /** Người dùng này có nằm trong {@code lecturerIds} của lớp đích không. */
    public boolean listedAsLecturer() {
      return scopeKind == ScopeKind.LISTED_AS_SECTION_LECTURER;
    }

    @Override
    public String toString() {
      return role + " / " + scopeKind + " / " + access + " -> " + (allowed ? "ALLOW" : "DENY");
    }
  }

  /** Khai báo gọn một dòng ma trận. */
  public static Row row(Role role, ScopeKind scope, boolean read, boolean write) {
    return new Row(role, scope, read, write);
  }

  /** Bung mỗi dòng thành hai ca READ và WRITE, dùng làm nguồn cho {@code @MethodSource}. */
  public static Stream<Case> cases(Row... rows) {
    return Stream.of(rows)
        .flatMap(
            r ->
                Stream.of(
                    new Case(r.role(), r.scope(), AccessType.READ, r.read()),
                    new Case(r.role(), r.scope(), AccessType.WRITE, r.write())));
  }
}
