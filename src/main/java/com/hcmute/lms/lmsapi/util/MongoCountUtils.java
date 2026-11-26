package com.hcmute.lms.lmsapi.util;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;

/** Tiện ích đếm số lượng bản ghi theo nhóm ObjectId bằng một lần MongoDB aggregation. */
public final class MongoCountUtils {

  private MongoCountUtils() {}

  public static Map<String, Long> countByObjectIdField(
      MongoTemplate mongoTemplate,
      Class<?> documentClass,
      String fieldName,
      Collection<String> ids) {
    if (mongoTemplate == null || ids == null || ids.isEmpty()) {
      return Map.of();
    }
    List<Object> criteriaIds =
        ids.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(id -> !id.isEmpty())
            .distinct()
            .<Object>map(id -> ObjectId.isValid(id) ? new ObjectId(id) : id)
            .toList();
    if (criteriaIds.isEmpty()) {
      return Map.of();
    }
    Aggregation aggregation =
        Aggregation.newAggregation(
            Aggregation.match(Criteria.where(fieldName).in(criteriaIds)),
            Aggregation.group(fieldName).count().as("count"));
    AggregationResults<Document> results =
        mongoTemplate.aggregate(aggregation, documentClass, Document.class);
    Map<String, Long> counts = new HashMap<>();
    for (Document doc : results.getMappedResults()) {
      Object rawId = doc.get("_id");
      Object rawCount = doc.get("count");
      if (rawId != null && rawCount instanceof Number num) {
        String key = rawId instanceof ObjectId oid ? oid.toHexString() : String.valueOf(rawId);
        counts.put(key, num.longValue());
      }
    }
    return counts;
  }
}
