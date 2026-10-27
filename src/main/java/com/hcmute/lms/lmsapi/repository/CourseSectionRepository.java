package com.hcmute.lms.lmsapi.repository;

import com.hcmute.lms.lmsapi.document.CourseSection;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository thao tác với collection course_sections (§4.17 / ADR 0003).
 * Chỉ đọc trong phân hệ F01, phục vụ xác thực quyền truy cập phạm vi (US-03).
 */
@Repository
public interface CourseSectionRepository extends MongoRepository<CourseSection, String> {

    /**
     * Tìm lớp học phần theo mã lớp (code).
     *
     * @param code mã lớp học phần
     * @return Optional chứa CourseSection nếu tìm thấy
     */
    Optional<CourseSection> findByCode(String code);

    /**
     * Kiểm tra sự tồn tại của lớp học phần theo mã lớp.
     *
     * @param code mã lớp học phần
     * @return true nếu tồn tại, ngược lại false
     */
    boolean existsByCode(String code);
}
