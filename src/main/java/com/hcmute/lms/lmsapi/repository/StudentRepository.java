package com.hcmute.lms.lmsapi.repository;

import com.hcmute.lms.lmsapi.document.Student;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentRepository
    extends MongoRepository<Student, String>, StudentRepositoryCustom {

  List<Student> findByStudentCodeIn(Collection<String> studentCodes);

  boolean existsByStudentCode(String studentCode);

  Optional<Student> findByStudentCode(String studentCode);
}
