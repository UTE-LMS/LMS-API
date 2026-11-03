package com.hcmute.lms.lmsapi.mapper.impl;

import com.hcmute.lms.lmsapi.document.Term;
import com.hcmute.lms.lmsapi.dto.TermRequest;
import com.hcmute.lms.lmsapi.dto.TermResponse;
import com.hcmute.lms.lmsapi.enums.TermStatus;
import com.hcmute.lms.lmsapi.mapper.TermMapper;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TermMapperImpl implements TermMapper {

  @Override
  public Term toDocument(TermRequest request) {
    if (request == null) {
      return null;
    }
    return Term.builder()
        .code(request.getCode().trim())
        .academicYear(request.getAcademicYear() != null ? request.getAcademicYear().trim() : null)
        .startDate(request.getStartDate())
        .endDate(request.getEndDate())
        .status(TermStatus.OPEN)
        .build();
  }

  @Override
  public TermResponse toResponse(Term term) {
    if (term == null) {
      return null;
    }
    return TermResponse.builder()
        .id(term.getId())
        .code(term.getCode())
        .academicYear(term.getAcademicYear())
        .startDate(term.getStartDate())
        .endDate(term.getEndDate())
        .status(term.getStatus())
        .build();
  }

  @Override
  public List<TermResponse> toResponseList(List<Term> terms) {
    if (terms == null || terms.isEmpty()) {
      return Collections.emptyList();
    }
    return terms.stream().map(this::toResponse).toList();
  }
}
