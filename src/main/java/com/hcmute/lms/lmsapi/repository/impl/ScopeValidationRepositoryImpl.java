package com.hcmute.lms.lmsapi.repository.impl;

import com.hcmute.lms.lmsapi.repository.ScopeValidationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

/**
 * Hiện thực interface {@link ScopeValidationRepository} bằng {@link MongoTemplate}.
 * Tuân thủ nghiêm ngặt ADR 0002: Toàn bộ thao tác truy vấn trực tiếp CSDL qua MongoTemplate
 * được đóng gói tại tầng repository.impl; tầng service tuyệt đối không gọi MongoTemplate trực tiếp.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ScopeValidationRepositoryImpl implements ScopeValidationRepository {

    public static final String COLLECTION_PROGRAMS = "programs";
    public static final String COLLECTION_COURSES = "courses";
    public static final String COLLECTION_COURSE_SECTIONS = "course_sections";

    private final MongoTemplate mongoTemplate;

    @Override
    public boolean existsProgram(String programId) {
        return existsInCollection(programId, COLLECTION_PROGRAMS);
    }

    @Override
    public boolean existsCourse(String courseId) {
        return existsInCollection(courseId, COLLECTION_COURSES);
    }

    @Override
    public boolean existsSection(String sectionId) {
        return existsInCollection(sectionId, COLLECTION_COURSE_SECTIONS);
    }

    /**
     * Kiểm tra sự tồn tại của bản ghi theo ID trong một collection cụ thể.
     * Hỗ trợ tìm kiếm cả kiểu chuỗi String lẫn BSON ObjectId.
     */
    private boolean existsInCollection(String id, String collectionName) {
        if (id == null || id.isBlank()) {
            return false;
        }

        Criteria criteria;
        if (ObjectId.isValid(id)) {
            criteria = new Criteria().orOperator(
                    Criteria.where("_id").is(id),
                    Criteria.where("_id").is(new ObjectId(id))
            );
        } else {
            criteria = Criteria.where("_id").is(id);
        }

        boolean exists = mongoTemplate.exists(new Query(criteria), collectionName);
        log.debug("Check exists for ID [{}] in collection [{}]: {}", id, collectionName, exists);
        return exists;
    }
}
