package com.hcmute.lms.lmsapi.repository.impl;

import com.hcmute.lms.lmsapi.document.AuditAction;
import com.hcmute.lms.lmsapi.document.AuditLog;
import com.hcmute.lms.lmsapi.repository.AuditLogRepositoryCustom;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Lớp thực thi các truy vấn tùy biến cho AuditLog sử dụng MongoTemplate.
 * Tuân thủ Open-Closed Principle (OCP) và Dependency Inversion Principle (DIP).
 */
@Repository
@RequiredArgsConstructor
public class AuditLogRepositoryCustomImpl implements AuditLogRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    @Override
    public List<AuditLog> findLogsByCriteria(String collection, String entityId, AuditAction action,
                                             Instant from, Instant to, int limit) {
        List<Criteria> criteriaList = new ArrayList<>();

        if (collection != null && !collection.isBlank()) {
            criteriaList.add(Criteria.where("entity.collection").is(collection));
        }

        if (entityId != null && !entityId.isBlank()) {
            criteriaList.add(Criteria.where("entity.id").is(entityId));
        }

        if (action != null) {
            criteriaList.add(Criteria.where("action").is(action));
        }

        if (from != null && to != null) {
            criteriaList.add(Criteria.where("at").gte(from).lte(to));
        } else if (from != null) {
            criteriaList.add(Criteria.where("at").gte(from));
        } else if (to != null) {
            criteriaList.add(Criteria.where("at").lte(to));
        }

        Query query = new Query();
        if (!criteriaList.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));
        }

        query.with(Sort.by(Sort.Direction.DESC, "at"));

        if (limit > 0) {
            query.limit(limit);
        }

        return mongoTemplate.find(query, AuditLog.class);
    }
}
