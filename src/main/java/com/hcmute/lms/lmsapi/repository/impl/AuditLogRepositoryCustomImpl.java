package com.hcmute.lms.lmsapi.repository.impl;

import com.hcmute.lms.lmsapi.document.AuditLog;
import com.hcmute.lms.lmsapi.enums.AuditAction;
import com.hcmute.lms.lmsapi.repository.AuditLogRepositoryCustom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

/**
 * Lớp thực thi các truy vấn tùy biến cho AuditLog sử dụng MongoTemplate. Tuân thủ Open-Closed
 * Principle (OCP) và Dependency Inversion Principle (DIP).
 */
@Repository
@RequiredArgsConstructor
public class AuditLogRepositoryCustomImpl implements AuditLogRepositoryCustom {

  private final MongoTemplate mongoTemplate;

  @Override
  public List<AuditLog> findLogsByCriteria(
      String collection, String entityId, AuditAction action, Instant from, Instant to, int limit) {
    Query query = buildQuery(null, collection, entityId, action, from, to);
    query.with(Sort.by(Sort.Direction.DESC, "at"));

    if (limit > 0) {
      query.limit(limit);
    }

    return mongoTemplate.find(query, AuditLog.class);
  }

  @Override
  public Page<AuditLog> searchLogs(
      String userId,
      String collection,
      String entityId,
      AuditAction action,
      Instant from,
      Instant to,
      Pageable pageable) {
    Query query = buildQuery(userId, collection, entityId, action, from, to);

    // Đếm trên truy vấn chưa phân trang; _id làm khóa phụ để thứ tự giữa các trang ổn định khi
    // nhiều dòng trùng thời điểm.
    long total = mongoTemplate.count(Query.of(query).limit(-1).skip(-1), AuditLog.class);

    query.with(pageable).with(Sort.by(Sort.Direction.DESC, "at").and(Sort.by("_id").descending()));
    List<AuditLog> content = mongoTemplate.find(query, AuditLog.class);

    return PageableExecutionUtils.getPage(content, pageable, () -> total);
  }

  private Query buildQuery(
      String userId,
      String collection,
      String entityId,
      AuditAction action,
      Instant from,
      Instant to) {
    List<Criteria> criteriaList = new ArrayList<>();

    if (userId != null && !userId.isBlank()) {
      // userId được lưu dạng BSON ObjectId (xem AuditLog.userId) nên phải so sánh bằng ObjectId.
      criteriaList.add(Criteria.where("userId").is(new ObjectId(userId.trim())));
    }

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
    return query;
  }
}
