package com.hcmute.lms.lmsapi.repository.impl;

import com.hcmute.lms.lmsapi.document.User;
import com.hcmute.lms.lmsapi.repository.UserRepositoryCustom;
import com.mongodb.client.result.UpdateResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

/**
 * Hiện thực interface {@link UserRepositoryCustom} bằng {@link MongoTemplate}.
 * Tuân thủ nghiêm ngặt ADR 0002: Toàn bộ các thao tác sử dụng MongoTemplate phải đóng gói
 * tại tầng repository.impl, tầng service tuyệt đối không gọi MongoTemplate trực tiếp.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class UserRepositoryCustomImpl implements UserRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    @Override
    public long incrementTokenVersion(String userId) {
        if (userId == null || userId.isBlank()) {
            return 0L;
        }

        // Hỗ trợ tìm kiếm cả kiểu chuỗi String hoặc BSON ObjectId
        Criteria criteria;
        if (ObjectId.isValid(userId)) {
            criteria = new Criteria().orOperator(
                    Criteria.where("_id").is(userId),
                    Criteria.where("_id").is(new ObjectId(userId))
            );
        } else {
            criteria = Criteria.where("_id").is(userId);
        }

        Query query = new Query(criteria);
        Update update = new Update().inc("tokenVersion", 1);

        UpdateResult result = mongoTemplate.updateFirst(query, update, User.class);
        log.debug("Incremented tokenVersion for user [{}], matched: {}, modified: {}",
                userId, result.getMatchedCount(), result.getModifiedCount());

        return result.getModifiedCount();
    }
}
