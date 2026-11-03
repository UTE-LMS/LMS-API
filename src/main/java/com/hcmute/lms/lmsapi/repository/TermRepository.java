package com.hcmute.lms.lmsapi.repository;

import com.hcmute.lms.lmsapi.document.Term;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/** Repository thao tác với collection terms (§4.16 / US-18). */
@Repository
public interface TermRepository extends MongoRepository<Term, String> {

  boolean existsByCode(String code);

  Optional<Term> findByCode(String code);

  List<Term> findAllByOrderByStartDateDesc();
}
