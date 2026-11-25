package com.hcmute.lms.lmsapi.service;

import com.hcmute.lms.lmsapi.dto.PageResponse;
import com.hcmute.lms.lmsapi.dto.TermRequest;
import com.hcmute.lms.lmsapi.dto.TermResponse;
import com.hcmute.lms.lmsapi.dto.TermStatusUpdateRequest;
import com.hcmute.lms.lmsapi.dto.TermUpdateRequest;
import com.hcmute.lms.lmsapi.enums.TermStatus;

public interface TermService {

  PageResponse<TermResponse> getTerms(String search, TermStatus status, int page, int size);

  TermResponse getTerm(String id);

  TermResponse createTerm(TermRequest request);

  TermResponse updateTerm(String id, TermUpdateRequest request);

  TermResponse updateStatus(String id, TermStatusUpdateRequest request, String actorUsername);

  void deleteTerm(String id);
}
