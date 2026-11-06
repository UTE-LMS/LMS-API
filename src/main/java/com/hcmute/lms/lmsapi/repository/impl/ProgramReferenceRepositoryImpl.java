package com.hcmute.lms.lmsapi.repository.impl;

import com.hcmute.lms.lmsapi.repository.ProgramReferenceRepository;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ProgramReferenceRepositoryImpl implements ProgramReferenceRepository {

  private final MongoTemplate mongoTemplate;

  @Override
  public boolean existsPiReference(String piId) {
    if (piId == null || !ObjectId.isValid(piId)) {
      return false;
    }
    ObjectId id = new ObjectId(piId);
    // ADR 0011: câu hỏi và bài đánh giá chỉ gán CLO (outcomeMappings.cloId), không gán piId
    // PI chỉ được tham chiếu qua clo_pi_links
    return existsInCollection("clo_pi_links", "piId", id);
  }

  private boolean existsInCollection(String collection, String field, ObjectId value) {
    Query query = Query.query(Criteria.where(field).is(value));
    return mongoTemplate.exists(query, collection);
  }
}
