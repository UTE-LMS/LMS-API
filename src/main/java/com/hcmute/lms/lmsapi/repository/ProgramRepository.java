package com.hcmute.lms.lmsapi.repository;

import com.hcmute.lms.lmsapi.document.Program;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProgramRepository extends MongoRepository<Program, String> {

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, String id);
}
