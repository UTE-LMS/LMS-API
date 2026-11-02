package com.hcmute.lms.lmsapi.repository;

import com.hcmute.lms.lmsapi.document.Course;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CourseRepository extends MongoRepository<Course, String> {
}
