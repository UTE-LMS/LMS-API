package com.hcmute.lms.lmsapi.mapper;

import com.hcmute.lms.lmsapi.document.Term;
import com.hcmute.lms.lmsapi.dto.TermRequest;
import com.hcmute.lms.lmsapi.dto.TermResponse;
import java.util.List;

public interface TermMapper {

  Term toDocument(TermRequest request);

  TermResponse toResponse(Term term);

  List<TermResponse> toResponseList(List<Term> terms);
}
