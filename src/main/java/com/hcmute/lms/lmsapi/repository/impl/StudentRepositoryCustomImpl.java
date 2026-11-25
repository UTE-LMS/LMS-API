package com.hcmute.lms.lmsapi.repository.impl;

import com.hcmute.lms.lmsapi.document.Student;
import com.hcmute.lms.lmsapi.enums.StudentStatus;
import com.hcmute.lms.lmsapi.repository.StudentRepositoryCustom;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class StudentRepositoryCustomImpl implements StudentRepositoryCustom {

  private final MongoTemplate mongoTemplate;

  @Override
  public Page<Student> searchStudents(
      String search, String cohort, StudentStatus status, Pageable pageable) {
    List<Criteria> criteriaList = new ArrayList<>();

    if (search != null && !search.isBlank()) {
      String escaped = Pattern.quote(search.trim());
      criteriaList.add(
          new Criteria()
              .orOperator(
                  Criteria.where("studentCode").regex(escaped, "i"),
                  Criteria.where("fullName").regex(escaped, "i")));
    }

    if (cohort != null && !cohort.isBlank()) {
      criteriaList.add(
          Criteria.where("cohort").regex("^" + Pattern.quote(cohort.trim()) + "$", "i"));
    }

    if (status != null) {
      criteriaList.add(Criteria.where("status").is(status));
    }

    Query query = new Query();
    if (!criteriaList.isEmpty()) {
      query.addCriteria(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));
    }

    long total = mongoTemplate.count(Query.of(query).limit(-1).skip(-1), Student.class);
    query.with(pageable).with(Sort.by(Sort.Direction.ASC, "studentCode").and(Sort.by("_id")));
    List<Student> content = mongoTemplate.find(query, Student.class);

    return PageableExecutionUtils.getPage(content, pageable, () -> total);
  }
}
