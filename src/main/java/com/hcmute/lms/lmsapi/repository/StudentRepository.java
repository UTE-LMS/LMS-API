package com.hcmute.lms.lmsapi.repository;

import com.hcmute.lms.lmsapi.document.Student;
import java.util.Collection;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentRepository extends MongoRepository<Student, String> {

  List<Student> findByStudentRefIn(Collection<String> studentRefs);
}
