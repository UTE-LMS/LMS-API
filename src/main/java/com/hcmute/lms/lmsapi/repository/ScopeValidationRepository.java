package com.hcmute.lms.lmsapi.repository;

/**
 * Interface kiểm tra sự tồn tại của các định danh tài nguyên trong Scope phụ trách.
 * Phục vụ kiểm tra hợp lệ của programIds, courseIds, sectionIds khi tạo hoặc cập nhật tài khoản (F01 / US-02).
 * Tuân thủ ADR 0002: Service không gọi trực tiếp MongoTemplate.
 */
public interface ScopeValidationRepository {

    /**
     * Kiểm tra sự tồn tại của chương trình đào tạo theo ID (collection programs).
     *
     * @param programId ID chương trình đào tạo
     * @return true nếu tồn tại trong CSDL, ngược lại false
     */
    boolean existsProgram(String programId);

    /**
     * Kiểm tra sự tồn tại của học phần theo ID (collection courses).
     *
     * @param courseId ID học phần
     * @return true nếu tồn tại trong CSDL, ngược lại false
     */
    boolean existsCourse(String courseId);

    /**
     * Kiểm tra sự tồn tại của lớp học phần theo ID (collection course_sections).
     *
     * @param sectionId ID lớp học phần
     * @return true nếu tồn tại trong CSDL, ngược lại false
     */
    boolean existsSection(String sectionId);
}
